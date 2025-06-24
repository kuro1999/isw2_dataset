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

    /**
     * @param repoDir directory radice del repo Git (contiene .git/)
     * @param tickets lista di JiraTicket da cui calcolare i fix
     * @return insieme di methodId (“FileName.java#methodSignature”) etichettati buggy
     */
    public static Set<String> computeBuggyMethods(
            File repoDir,
            List<JiraTicket> tickets
    ) throws Exception {
        // 1) apri il repository
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
        Git git = new Git(repository);

        // 2) individua tutti i ticket con resolution=Fixed e status Closed/Resolved
        Set<String> defectKeys = new HashSet<>();
        for (JiraTicket t : tickets) {
            if ("Fixed".equalsIgnoreCase(t.getResolution())
                    && ("Closed".equalsIgnoreCase(t.getStatus())
                    || "Resolved".equalsIgnoreCase(t.getStatus()))) {
                defectKeys.add(t.getKey());
            }
        }
        logger.info("» Totale ticket con resolution=Fixed & Closed/Resolved: {}", defectKeys.size());

        // 3) trova i commit che menzionano queste key
        Set<ObjectId> fixCommits = new HashSet<>();
        for (RevCommit c : git.log().call()) {
            String msg = c.getFullMessage();
            for (String key : defectKeys) {
                if (msg.contains(key)) {
                    fixCommits.add(c.getId());
                    break;
                }
            }
        }
        logger.info("» Trovati {} commit che menzionano ticket Fixed", fixCommits.size());

        // 4) per ciascun fix commit, diff e raccolta methodId cambiati
        Set<String> buggyMethods = new HashSet<>();
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);

        for (ObjectId cid : fixCommits) {
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(cid);
                if (commit.getParentCount() == 0) continue;
                RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());

                AbstractTreeIterator pt = prepareTree(repository, parent);
                AbstractTreeIterator ct = prepareTree(repository, commit);

                List<DiffEntry> diffs = df.scan(pt, ct);
                for (DiffEntry diff : diffs) {
                    if (diff.getChangeType() != DiffEntry.ChangeType.MODIFY) continue;
                    String path = diff.getNewPath();
                    if (!path.endsWith(".java")) continue;

                    ObjectLoader loader = repository.open(diff.getNewId().toObjectId());
                    String src = new String(loader.getBytes(), StandardCharsets.UTF_8);
                    CompilationUnit cu = StaticJavaParser.parse(src);

                    FileHeader fh = df.toFileHeader(diff);
                    EditList edits = fh.toEditList();
                    Set<Integer> changed = new HashSet<>();
                    for (Edit e : edits)
                        for (int ln = e.getBeginB(); ln < e.getEndB(); ln++)
                            changed.add(ln + 1);

                    for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
                        md.getRange().ifPresent(r -> {
                            int b = r.begin.line, eLine = r.end.line;
                            for (int ln : changed) {
                                if (ln >= b && ln <= eLine) {
                                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                                    String sig = md.getDeclarationAsString(false, false, false);
                                    buggyMethods.add(fileName + "#" + sig);
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
        return buggyMethods;
    }

    /** Helper: prepara un TreeIterator per un RevCommit */
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
