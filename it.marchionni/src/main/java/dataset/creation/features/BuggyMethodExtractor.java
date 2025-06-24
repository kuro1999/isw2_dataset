package dataset.creation.features;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import dataset.creation.fetcher.model.JiraTicket;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BuggyMethodExtractor {

    private static final Logger logger = LoggerFactory.getLogger(BuggyMethodExtractor.class);

    /** Risultato composto: set di metodi buggy + mappa methodId → priority */
    public static class BuggyInfo {
        public final Set<String> buggyMethods;
        public BuggyInfo(Set<String> b, Map<String,String> p){ buggyMethods=b; }
    }

    /**
     * @param repoDir  directory radice del repo Git (contiene .git/)
     * @param tickets  lista di JiraTicket
     * @return BuggyInfo con metodi buggy e priority
     */
    public static BuggyInfo computeBuggyMethods(
            File repoDir,
            List<JiraTicket> tickets
    ) throws Exception {

        /* ---------- 1. Apri repo ----------- */
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
        Git git = new Git(repository);

        /* ---------- 2. Ticket “Fixed” ----------- */
        Map<String,String> keyToPriority = new HashMap<>();
        for (JiraTicket t : tickets) {
            if ("Fixed".equalsIgnoreCase(t.getResolution())
                    && ("Closed".equalsIgnoreCase(t.getStatus())
                    || "Resolved".equalsIgnoreCase(t.getStatus()))) {
                keyToPriority.put(t.getKey(), t.getPriority());  // key → priority string
            }
        }
        logger.info("» Ticket fix rilevati: {}", keyToPriority.size());

        /* ---------- 3. Commit che citano i ticket ----------- */
        Map<ObjectId,String> commitToPriority = new HashMap<>();
        for (RevCommit c : git.log().call()) {
            String msg = c.getFullMessage();
            for (Map.Entry<String,String> e : keyToPriority.entrySet()) {
                if (msg.contains(e.getKey())) {
                    commitToPriority.put(c.getId(), e.getValue());
                    break;
                }
            }
        }
        logger.info("» Commit di fix trovati: {}", commitToPriority.size());

        /* ---------- 4. Diff e raccolta metodi buggy ----------- */
        Set<String> buggyMethods = new HashSet<>();
        Map<String,String> prioOfMethod = new HashMap<>();

        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);

        for (Map.Entry<ObjectId,String> entry : commitToPriority.entrySet()) {
            ObjectId cid   = entry.getKey();
            String   prio  = entry.getValue();

            try (RevWalk rw = new RevWalk(repository)) {
                RevCommit commit = rw.parseCommit(cid);
                if (commit.getParentCount() == 0) continue;
                RevCommit parent = rw.parseCommit(commit.getParent(0).getId());

                AbstractTreeIterator pt = prepareTree(repository, parent);
                AbstractTreeIterator ct = prepareTree(repository, commit);

                for (DiffEntry diff : df.scan(pt, ct)) {
                    if (diff.getChangeType() != DiffEntry.ChangeType.MODIFY) continue;
                    String path = diff.getNewPath();
                    if (!path.endsWith(".java")) continue;

                    ObjectLoader loader = repository.open(diff.getNewId().toObjectId());
                    String src = new String(loader.getBytes(), StandardCharsets.UTF_8);
                    CompilationUnit cu = StaticJavaParser.parse(src);

                    FileHeader fh = df.toFileHeader(diff);
                    Set<Integer> changed = new HashSet<>();
                    for (Edit e : fh.toEditList())
                        for (int ln = e.getBeginB(); ln < e.getEndB(); ln++)
                            changed.add(ln + 1);

                    for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
                        md.getRange().ifPresent(r -> {
                            int b = r.begin.line, eLine = r.end.line;
                            for (int ln : changed) {
                                if (ln >= b && ln <= eLine) {
                                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                                    String sig = md.getDeclarationAsString(false, false, false);
                                    String id  = fileName + "#" + sig;
                                    buggyMethods.add(id);
                                    prioOfMethod.put(id, prio);        // salva priority
                                    break;
                                }
                            }
                        });
                    }
                }
            }
        }

        df.close();
        repository.close();
        return new BuggyInfo(buggyMethods, prioOfMethod);
    }

    /* Helper: TreeIterator per un RevCommit */
    private static AbstractTreeIterator prepareTree(Repository repo, RevCommit commit) throws Exception {
        try (RevWalk rw = new RevWalk(repo)) {
            RevTree tree = rw.parseTree(commit.getTree().getId());
            CanonicalTreeParser p = new CanonicalTreeParser();
            try (ObjectReader r = repo.newObjectReader()) {
                p.reset(r, tree.getId());
            }
            return p;
        }
    }
}
