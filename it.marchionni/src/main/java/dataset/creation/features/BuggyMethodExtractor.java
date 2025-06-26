package dataset.creation.features;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import dataset.creation.fetcher.model.JiraTicket;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Estende il calcolo "buggy" includendo statistiche:
 *  - total churn, avg/max added/deleted/churn
 *  - condChanges (# di if/else/case modificate)
 *  - histories (# commit per metodo)
 *  - authors (# autori distinti per metodo)
 */
public class BuggyMethodExtractor {

    private static final Logger logger = LoggerFactory.getLogger(BuggyMethodExtractor.class);
    private static final String CACHE_FILE = "buggy_info_cache.json";

    public static class BuggyInfo {
        public final Set<String> buggyMethods;
        public final Map<String,Integer> churnOfMethod;
        public final Map<String,Integer> elseAddedOfMethod;
        public final Map<String,Integer> elseDeletedOfMethod;
        public final Map<String,Integer> condChangesOfMethod;
        public final Map<String,Integer> methodHistoriesOfMethod;
        public final Map<String,Integer> authorsOfMethod;
        public final Map<String,Double> avgAddedOfMethod;
        public final Map<String,Integer> maxAddedOfMethod;
        public final Map<String,Double> avgDeletedOfMethod;
        public final Map<String,Integer> maxDeletedOfMethod;
        public final Map<String,Double> avgChurnOfMethod;
        public final Map<String,Integer> maxChurnOfMethod;

        public BuggyInfo(Set<String> buggy,
                         Map<String,Integer> churn,
                         Map<String,Integer> elseAdd,
                         Map<String,Integer> elseDel,
                         Map<String,Integer> condCh,
                         Map<String,Integer> histories,
                         Map<String,Integer> authors,
                         Map<String,Double> avgAdd,
                         Map<String,Integer> maxAdd,
                         Map<String,Double> avgDel,
                         Map<String,Integer> maxDel,
                         Map<String,Double> avgCh,
                         Map<String,Integer> maxCh) {
            this.buggyMethods = buggy;
            this.churnOfMethod = churn;
            this.elseAddedOfMethod = elseAdd;
            this.elseDeletedOfMethod = elseDel;
            this.condChangesOfMethod = condCh;
            this.methodHistoriesOfMethod = histories;
            this.authorsOfMethod = authors;
            this.avgAddedOfMethod = avgAdd;
            this.maxAddedOfMethod = maxAdd;
            this.avgDeletedOfMethod = avgDel;
            this.maxDeletedOfMethod = maxDel;
            this.avgChurnOfMethod = avgCh;
            this.maxChurnOfMethod = maxCh;
        }

        public BuggyInfoDTO toDto() {
            BuggyInfoDTO dto = new BuggyInfoDTO();
            dto.buggyMethods = new ArrayList<>(buggyMethods);
            dto.churnOfMethod = churnOfMethod;
            dto.elseAddedOfMethod = elseAddedOfMethod;
            dto.elseDeletedOfMethod = elseDeletedOfMethod;
            dto.condChangesOfMethod = condChangesOfMethod;
            dto.methodHistoriesOfMethod = methodHistoriesOfMethod;
            dto.authorsOfMethod = authorsOfMethod;
            dto.avgAddedOfMethod = avgAddedOfMethod;
            dto.maxAddedOfMethod = maxAddedOfMethod;
            dto.avgDeletedOfMethod = avgDeletedOfMethod;
            dto.maxDeletedOfMethod = maxDeletedOfMethod;
            dto.avgChurnOfMethod = avgChurnOfMethod;
            dto.maxChurnOfMethod = maxChurnOfMethod;
            return dto;
        }

        public static BuggyInfo fromDto(BuggyInfoDTO dto) {
            Set<String> buggy = dto.buggyMethods != null
                    ? new HashSet<>(dto.buggyMethods)
                    : new HashSet<>();
            Map<String,Integer> churn = dto.churnOfMethod != null
                    ? dto.churnOfMethod
                    : new HashMap<>();
            Map<String,Integer> elseAdd = dto.elseAddedOfMethod != null
                    ? dto.elseAddedOfMethod
                    : new HashMap<>();
            Map<String,Integer> elseDel = dto.elseDeletedOfMethod != null
                    ? dto.elseDeletedOfMethod
                    : new HashMap<>();
            Map<String,Integer> condCh = dto.condChangesOfMethod != null
                    ? dto.condChangesOfMethod
                    : new HashMap<>();
            Map<String,Integer> histories = dto.methodHistoriesOfMethod != null
                    ? dto.methodHistoriesOfMethod
                    : new HashMap<>();
            Map<String,Integer> authors = dto.authorsOfMethod != null
                    ? dto.authorsOfMethod
                    : new HashMap<>();
            Map<String,Double> avgAdd = dto.avgAddedOfMethod != null
                    ? dto.avgAddedOfMethod
                    : new HashMap<>();
            Map<String,Integer> maxAdd = dto.maxAddedOfMethod != null
                    ? dto.maxAddedOfMethod
                    : new HashMap<>();
            Map<String,Double> avgDel = dto.avgDeletedOfMethod != null
                    ? dto.avgDeletedOfMethod
                    : new HashMap<>();
            Map<String,Integer> maxDel = dto.maxDeletedOfMethod != null
                    ? dto.maxDeletedOfMethod
                    : new HashMap<>();
            Map<String,Double> avgCh = dto.avgChurnOfMethod != null
                    ? dto.avgChurnOfMethod
                    : new HashMap<>();
            Map<String,Integer> maxCh = dto.maxChurnOfMethod != null
                    ? dto.maxChurnOfMethod
                    : new HashMap<>();

            return new BuggyInfo(
                    buggy,
                    churn,
                    elseAdd,
                    elseDel,
                    condCh,
                    histories,
                    authors,
                    avgAdd,
                    maxAdd,
                    avgDel,
                    maxDel,
                    avgCh,
                    maxCh
            );
        }
    }

    public static class BuggyInfoDTO {
        public List<String> buggyMethods;
        public Map<String,Integer> churnOfMethod;
        public Map<String,Integer> elseAddedOfMethod;
        public Map<String,Integer> elseDeletedOfMethod;
        public Map<String,Integer> condChangesOfMethod;
        public Map<String,Integer> methodHistoriesOfMethod;
        public Map<String,Integer> authorsOfMethod;
        public Map<String,Double> avgAddedOfMethod;
        public Map<String,Integer> maxAddedOfMethod;
        public Map<String,Double> avgDeletedOfMethod;
        public Map<String,Integer> maxDeletedOfMethod;
        public Map<String,Double> avgChurnOfMethod;
        public Map<String,Integer> maxChurnOfMethod;
    }

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

    private static BuggyInfo computeBuggyMethods(File repoDir, List<JiraTicket> tickets) throws Exception {
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment().findGitDir().build();
        Git git = new Git(repository);

        Set<String> defectKeys = new HashSet<>();
        for (JiraTicket t : tickets) {
            if ("Fixed".equalsIgnoreCase(t.getResolution())
                    && ("Closed".equalsIgnoreCase(t.getStatus()) || "Resolved".equalsIgnoreCase(t.getStatus()))) {
                defectKeys.add(t.getKey());
            }
        }
        logger.info("» Ticket fix rilevati: {}", defectKeys.size());

        Set<ObjectId> fixCommits = new HashSet<>();
        for (RevCommit c : git.log().call()) {
            for (String key : defectKeys) {
                if (c.getFullMessage().contains(key)) {
                    fixCommits.add(c.getId());
                    break;
                }
            }
        }
        logger.info("» Commit di fix trovati: {}", fixCommits.size());

        Set<String> buggyMethods = new HashSet<>();
        Map<String,Integer> churnMap = new HashMap<>();
        Map<String,Integer> elseAddMap = new HashMap<>();
        Map<String,Integer> elseDelMap = new HashMap<>();
        Map<String,Integer> condMap = new HashMap<>();
        Map<String,List<Integer>> addList = new HashMap<>();
        Map<String,List<Integer>> delList = new HashMap<>();
        Map<String,List<Integer>> churnList = new HashMap<>();
        Map<String,Set<String>> authorMap = new HashMap<>();
        Map<String,Integer> histories = new HashMap<>();

        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);

        for (ObjectId cid : fixCommits) {
            try (RevWalk rw = new RevWalk(repository)) {
                RevCommit commit = rw.parseCommit(cid);
                RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
                AbstractTreeIterator pt = prepareTree(repository, parent);
                AbstractTreeIterator ct = prepareTree(repository, commit);

                String author = commit.getAuthorIdent().getName();
                for (DiffEntry diff : df.scan(pt, ct)) {
                    if (diff.getChangeType() != DiffEntry.ChangeType.MODIFY) continue;
                    String path = diff.getNewPath();
                    if (!path.endsWith(".java")) continue;

                    String oldSrc = new String(repository.open(diff.getOldId().toObjectId()).getBytes(), StandardCharsets.UTF_8);
                    String newSrc = new String(repository.open(diff.getNewId().toObjectId()).getBytes(), StandardCharsets.UTF_8);
                    List<String> oldLines = Arrays.asList(oldSrc.split("\r?\n", -1));
                    List<String> newLines = Arrays.asList(newSrc.split("\r?\n", -1));

                    FileHeader fh = df.toFileHeader(diff);
                    for (var e : fh.toEditList()) {
                        int added = e.getEndB() - e.getBeginB();
                        int deleted = e.getEndA() - e.getBeginA();
                        int delta = added + deleted;

                        CompilationUnit cu = StaticJavaParser.parse(newSrc);
                        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
                            md.getRange().ifPresent(r -> {
                                if (e.getBeginB()+1 <= r.end.line && e.getEndB() >= r.begin.line) {
                                    String id = new File(path).getName() + "#" + md.getDeclarationAsString(false,false,false);
                                    buggyMethods.add(id);
                                    churnMap.merge(id, delta, Integer::sum);
                                    addList.computeIfAbsent(id, k->new ArrayList<>()).add(added);
                                    delList.computeIfAbsent(id, k->new ArrayList<>()).add(deleted);
                                    churnList.computeIfAbsent(id, k->new ArrayList<>()).add(delta);
                                    authorMap.computeIfAbsent(id, k->new HashSet<>()).add(author);
                                    histories.merge(id, 1, Integer::sum);

                                    for (int ln=e.getBeginB()+1; ln<=e.getEndB(); ln++) if (newLines.get(ln-1).contains("else")) elseAddMap.merge(id,1,Integer::sum);
                                    for (int ln=e.getBeginA()+1; ln<=e.getEndA(); ln++) if (oldLines.get(ln-1).contains("else")) elseDelMap.merge(id,1,Integer::sum);
                                    for (int ln=e.getBeginB()+1; ln<=e.getEndB(); ln++) {
                                        String l=newLines.get(ln-1);
                                        if (l.contains("if")||l.contains("case")) condMap.merge(id,1,Integer::sum);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }
        df.close(); repository.close();

        Map<String,Double> avgAdd = new HashMap<>();
        Map<String,Integer> mAdd = new HashMap<>();
        Map<String,Double> avgDel = new HashMap<>();
        Map<String,Integer> mDel = new HashMap<>();
        Map<String,Double> avgCh = new HashMap<>();
        Map<String,Integer> mCh = new HashMap<>();
        for (String id : churnList.keySet()) {
            List<Integer> adds = addList.getOrDefault(id, List.of());
            avgAdd.put(id, adds.stream().mapToInt(x->x).average().orElse(0));
            mAdd.put(id, adds.stream().mapToInt(x->x).max().orElse(0));
            List<Integer> dels = delList.getOrDefault(id, List.of());
            avgDel.put(id, dels.stream().mapToInt(x->x).average().orElse(0));
            mDel.put(id, dels.stream().mapToInt(x->x).max().orElse(0));
            List<Integer> chs = churnList.get(id);
            avgCh.put(id, chs.stream().mapToInt(x->x).average().orElse(0));
            mCh.put(id, chs.stream().mapToInt(x->x).max().orElse(0));
        }

        return new BuggyInfo(
                buggyMethods,
                churnMap,
                elseAddMap,
                elseDelMap,
                condMap,
                histories,
                authorMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e->e.getValue().size())),
                avgAdd, mAdd, avgDel, mDel, avgCh, mCh
        );
    }

    private static AbstractTreeIterator prepareTree(Repository repo, RevCommit commit) throws Exception {
        RevTree tree;
        try (RevWalk rw = new RevWalk(repo)) {
            tree = rw.parseTree(commit.getTree().getId());
        }
        CanonicalTreeParser p = new CanonicalTreeParser();
        try (var reader = repo.newObjectReader()) {
            p.reset(reader, tree.getId());
        }
        return p;
    }
}
