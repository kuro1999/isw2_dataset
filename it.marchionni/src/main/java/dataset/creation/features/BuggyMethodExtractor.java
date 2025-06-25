package dataset.creation.features;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import dataset.creation.fetcher.model.JiraTicket;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Individua i metodi modificati (buggy) nei commit di fix,
 * ne calcola churn, else‐added e else‐deleted, e supporta caching.
 */
public class BuggyMethodExtractor {

    private static final Logger logger = LoggerFactory.getLogger(BuggyMethodExtractor.class);
    private static final String CACHE_FILE = "buggy_info_cache.json";

    /**
     * Risultato:
     *  • buggyMethods           = insieme di methodId (“File.java#signature”)
     *  • churnOfMethod          = churn totale (added+deleted) per methodId
     *  • elseAddedOfMethod      = numero di “else” aggiunti per methodId
     *  • elseDeletedOfMethod    = numero di “else” rimossi per methodId
     */
    public static class BuggyInfo {
        public final Set<String>            buggyMethods;
        public final Map<String,Integer>    churnOfMethod;
        public final Map<String,Integer>    elseAddedOfMethod;
        public final Map<String,Integer>    elseDeletedOfMethod;

        public BuggyInfo(Set<String> b,
                         Map<String,Integer> c,
                         Map<String,Integer> ea,
                         Map<String,Integer> ed) {
            this.buggyMethods        = b;
            this.churnOfMethod       = c;
            this.elseAddedOfMethod   = ea;
            this.elseDeletedOfMethod = ed;
        }

        public BuggyInfoDTO toDto() {
            BuggyInfoDTO dto = new BuggyInfoDTO();
            dto.buggyMethods         = new ArrayList<>(buggyMethods);
            dto.churnOfMethod        = churnOfMethod;
            dto.elseAddedOfMethod    = elseAddedOfMethod;
            dto.elseDeletedOfMethod  = elseDeletedOfMethod;
            return dto;
        }
        public static BuggyInfo fromDto(BuggyInfoDTO dto) {
            return new BuggyInfo(
                    new HashSet<>(dto.buggyMethods),
                    dto.churnOfMethod,
                    dto.elseAddedOfMethod,
                    dto.elseDeletedOfMethod
            );
        }
    }

    /** DTO per il JSON cache */
    public static class BuggyInfoDTO {
        public List<String>          buggyMethods;
        public Map<String,Integer>   churnOfMethod;
        public Map<String,Integer>   elseAddedOfMethod;
        public Map<String,Integer>   elseDeletedOfMethod;
    }

    /**
     * Carica la cache se esiste, altrimenti calcola e salva su disco.
     */
    public static BuggyInfo computeOrLoad(File repoDir, List<JiraTicket> tickets) throws Exception {
        File cache = new File(CACHE_FILE);
        if (cache.exists()) {
            logger.info("▶ Carico cache da {}", CACHE_FILE);
            try (Reader r = new FileReader(cache)) {
                Jsonb jsonb = JsonbBuilder.create();
                BuggyInfoDTO dto = jsonb.fromJson(r, BuggyInfoDTO.class);
                return BuggyInfo.fromDto(dto);
            }
        }
        logger.info("▶ Nessuna cache: avvio calcolo metodi buggy + churn/else");
        BuggyInfo info = computeBuggyMethods(repoDir, tickets);
        try (Writer w = new FileWriter(cache)) {
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(info.toDto(), w);
            logger.info("✅ Cache salvata in {}", CACHE_FILE);
        }
        return info;
    }

    /**
     * Analizza i commit di fix, raccoglie:
     *   • churn (added+deleted)
     *   • elseAdded
     *   • elseDeleted
     * per ogni metodo modificato.
     */
    private static BuggyInfo computeBuggyMethods(
            File repoDir,
            List<JiraTicket> tickets
    ) throws Exception {

        // 1) Apri il repository
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
        Git git = new Git(repository);

        // 2) Filtra i ticket Fixed + (Closed|Resolved)
        Set<String> defectKeys = new HashSet<>();
        for (JiraTicket t : tickets) {
            if ("Fixed".equalsIgnoreCase(t.getResolution())
                    && ("Closed".equalsIgnoreCase(t.getStatus())
                    || "Resolved".equalsIgnoreCase(t.getStatus()))) {
                defectKeys.add(t.getKey());
            }
        }
        logger.info("» Ticket fix rilevati: {}", defectKeys.size());

        // 3) Trova i commit che menzionano quei ticket
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
        logger.info("» Commit di fix trovati: {}", fixCommits.size());

        // 4) Per ogni commit: diff, churn, elseAdded, elseDeleted
        Set<String> buggyMethods              = new HashSet<>();
        Map<String,Integer> churnMap          = new HashMap<>();
        Map<String,Integer> elseAddedMap      = new HashMap<>();
        Map<String,Integer> elseDeletedMap    = new HashMap<>();

        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);

        for (ObjectId cid : fixCommits) {
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

                    // carica vecchia e nuova versione per "else"
                    List<String> oldLines = Arrays.asList(
                            new String(repository.open(diff.getOldId().toObjectId()).getBytes(), StandardCharsets.UTF_8)
                                    .split("\r?\n", -1));
                    List<String> newLines = Arrays.asList(
                            new String(repository.open(diff.getNewId().toObjectId()).getBytes(), StandardCharsets.UTF_8)
                                    .split("\r?\n", -1));

                    FileHeader fh = df.toFileHeader(diff);
                    for (Edit e : fh.toEditList()) {
                        int added   = e.getEndB() - e.getBeginB();
                        int deleted = e.getEndA() - e.getBeginA();
                        int delta   = added + deleted;

                        // parsing file nuovo per estrarre i metodi
                        CompilationUnit cu = StaticJavaParser.parse(
                                new String(repository.open(diff.getNewId().toObjectId()).getBytes(), StandardCharsets.UTF_8)
                        );

                        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
                            md.getRange().ifPresent(r -> {
                                int mb = r.begin.line, me = r.end.line;
                                // churn + elseAdded
                                for (int ln = e.getBeginB() + 1; ln <= e.getEndB(); ln++) {
                                    if (ln >= mb && ln <= me) {
                                        String fileName = path.substring(path.lastIndexOf('/') + 1);
                                        String sig      = md.getDeclarationAsString(false,false,false);
                                        String id       = fileName + "#" + sig;

                                        buggyMethods.add(id);
                                        churnMap.merge(id, delta, Integer::sum);

                                        if (newLines.get(ln-1).contains("else")) {
                                            elseAddedMap.merge(id, 1, Integer::sum);
                                        }
                                        break;
                                    }
                                }
                                // elseDeleted
                                for (int ln = e.getBeginA() + 1; ln <= e.getEndA(); ln++) {
                                    if (ln >= mb && ln <= me && oldLines.get(ln-1).contains("else")) {
                                        String fileName = path.substring(path.lastIndexOf('/') + 1);
                                        String sig      = md.getDeclarationAsString(false,false,false);
                                        String id       = fileName + "#" + sig;

                                        elseDeletedMap.merge(id, 1, Integer::sum);
                                        break;
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }

        df.close();
        repository.close();
        return new BuggyInfo(buggyMethods, churnMap, elseAddedMap, elseDeletedMap);
    }

    /** Helper: costruisce un TreeIterator per un RevCommit */
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
