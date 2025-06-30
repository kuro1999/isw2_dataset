package dataset.creation.features;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import dataset.creation.fetcher.model.JiraTicket;   // mantenuto per compatibilità firma

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import com.github.javaparser.Range;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;



import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Estende il calcolo "buggy" usando il diff tra release consecutive,
 * senza PrettyPrinter ma rimuovendo i commenti via AST.
 */
public class BuggyMethodExtractor {
    private static final Logger logger = LoggerFactory.getLogger(BuggyMethodExtractor.class);

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
            this.buggyMethods        = buggy;
            this.churnOfMethod       = churn;
            this.elseAddedOfMethod   = elseAdd;
            this.elseDeletedOfMethod = elseDel;
            this.condChangesOfMethod = condCh;
            this.methodHistoriesOfMethod = histories;
            this.authorsOfMethod     = authors;
            this.avgAddedOfMethod    = avgAdd;
            this.maxAddedOfMethod    = maxAdd;
            this.avgDeletedOfMethod  = avgDel;
            this.maxDeletedOfMethod  = maxDel;
            this.avgChurnOfMethod    = avgCh;
            this.maxChurnOfMethod    = maxCh;
        }

        public BuggyInfoDTO toDto() {
            BuggyInfoDTO dto = new BuggyInfoDTO();
            dto.buggyMethods           = new ArrayList<>(buggyMethods);
            dto.churnOfMethod          = churnOfMethod;
            dto.elseAddedOfMethod      = elseAddedOfMethod;
            dto.elseDeletedOfMethod    = elseDeletedOfMethod;
            dto.condChangesOfMethod    = condChangesOfMethod;
            dto.methodHistoriesOfMethod= methodHistoriesOfMethod;
            dto.authorsOfMethod        = authorsOfMethod;
            dto.avgAddedOfMethod       = avgAddedOfMethod;
            dto.maxAddedOfMethod       = maxAddedOfMethod;
            dto.avgDeletedOfMethod     = avgDeletedOfMethod;
            dto.maxDeletedOfMethod     = maxDeletedOfMethod;
            dto.avgChurnOfMethod       = avgChurnOfMethod;
            dto.maxChurnOfMethod       = maxChurnOfMethod;
            return dto;
        }

        public static BuggyInfo fromDto(BuggyInfoDTO dto) {
            Set<String> buggy = dto.buggyMethods != null
                    ? new HashSet<>(dto.buggyMethods)
                    : new HashSet<>();
            Map<String,Integer> churn = dto.churnOfMethod != null
                    ? dto.churnOfMethod : new HashMap<>();
            Map<String,Integer> elseAdd = dto.elseAddedOfMethod != null
                    ? dto.elseAddedOfMethod : new HashMap<>();
            Map<String,Integer> elseDel = dto.elseDeletedOfMethod != null
                    ? dto.elseDeletedOfMethod : new HashMap<>();
            Map<String,Integer> condCh = dto.condChangesOfMethod != null
                    ? dto.condChangesOfMethod : new HashMap<>();
            Map<String,Integer> histories = dto.methodHistoriesOfMethod != null
                    ? dto.methodHistoriesOfMethod : new HashMap<>();
            Map<String,Integer> authors = dto.authorsOfMethod != null
                    ? dto.authorsOfMethod : new HashMap<>();
            Map<String,Double> avgAdd = dto.avgAddedOfMethod != null
                    ? dto.avgAddedOfMethod : new HashMap<>();
            Map<String,Integer> maxAdd = dto.maxAddedOfMethod != null
                    ? dto.maxAddedOfMethod : new HashMap<>();
            Map<String,Double> avgDel = dto.avgDeletedOfMethod != null
                    ? dto.avgDeletedOfMethod : new HashMap<>();
            Map<String,Integer> maxDel = dto.maxDeletedOfMethod != null
                    ? dto.maxDeletedOfMethod : new HashMap<>();
            Map<String,Double> avgCh = dto.avgChurnOfMethod != null
                    ? dto.avgChurnOfMethod : new HashMap<>();
            Map<String,Integer> maxCh = dto.maxChurnOfMethod != null
                    ? dto.maxChurnOfMethod : new HashMap<>();

            return new BuggyInfo(
                    buggy,
                    churn,
                    elseAdd,
                    elseDel,
                    condCh,
                    histories,
                    authors,
                    avgAdd, maxAdd,
                    avgDel, maxDel,
                    avgCh, maxCh
            );
        }
    }

    public static BuggyInfo computeOrLoad(
            File repoDir,
            List<JiraTicket> tickets,
            String projectKey,
            Path cacheDir
    ) throws Exception {
        String cacheFileName = projectKey + "_buggy_info_cache.json";
        File cache = cacheDir.resolve(cacheFileName).toFile();
        /*if (cache.exists()) {
            System.out.println("🟡 Buggy methods cache esistente, caricamento da: " + cache);
            try (Reader r = new FileReader(cache)) {
                Jsonb jsonb = JsonbBuilder.create();
                return BuggyInfo.fromDto(jsonb.fromJson(r, BuggyInfoDTO.class));
            }
        }*/
        System.out.println("🔵 Nessuna cache trovata, computo da zero");
        BuggyInfo info = computeBuggyMethods(repoDir, tickets, projectKey);
        try (Writer w = new FileWriter(cache)) {
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(info.toDto(), w);
            logger.info("✅ Cache salvata in {}", cache.getPath());
        }
        return info;
    }


    private static BuggyInfo computeBuggyMethods(File repoDir, List<JiraTicket> tickets,
                                                 String projectKey) throws Exception {
        // 1. Filtra ticket di bug validi con più controllo
        List<JiraTicket> bugTickets = (List<JiraTicket>) tickets.stream()
                .filter(t -> "bug".equalsIgnoreCase(t.getIssueType()))
                .filter(t -> "Fixed".equalsIgnoreCase(t.getResolution()))
                .filter(t -> Set.of("Closed", "Resolved").contains(t.getStatus()))
                .filter(t -> t.getKey() != null && !t.getKey().isEmpty()).collect(Collectors.toList());

        logger.info("Ticket di bug trovati: {}", bugTickets.size());
        if (bugTickets.isEmpty()) {
            logger.warn("ATTENZIONE: Nessun ticket di bug valido trovato!");
            return emptyInfo();
        }

        // 2. Setup repository con più opzioni
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .setMustExist(true)
                .readEnvironment()
                .findGitDir()
                .build();

        Git git = new Git(repository);

        // 3. Recupera tutti i tag con più robustezza
        List<Ref> tags = git.tagList().call();
        if (tags.isEmpty()) {
            tags = git.lsRemote()
                    .setRemote("origin")
                    .setTags(true)
                    .setHeads(false)
                    .call()
                    .stream()
                    .filter(r -> r.getName().startsWith("refs/tags/"))
                    .collect(Collectors.toList());
        }

        // Ordina i tag per data
        tags.sort(Comparator.comparing(ref -> {
            try (RevWalk rw = new RevWalk(repository)) {
                ObjectId id = Optional.ofNullable(ref.getPeeledObjectId())
                        .orElse(ref.getObjectId());
                return rw.parseCommit(id).getCommitTime();
            } catch (Exception e) {
                logger.error("Errore ordinamento tag", e);
                return 0;
            }
        }));

        // 4. Strutture dati migliorate
        Set<String> buggyMethods = new HashSet<>();
        Map<String, Integer> churnMap = new HashMap<>();
        Map<String, Integer> elseAddMap = new HashMap<>();
        Map<String, Integer> elseDelMap = new HashMap<>();
        Map<String, Integer> condMap = new HashMap<>();
        Map<String, List<Integer>> addList = new HashMap<>();
        Map<String, List<Integer>> delList = new HashMap<>();
        Map<String, List<Integer>> churnList = new HashMap<>();
        Map<String, Set<String>> authorMap = new HashMap<>();
        Map<String, Integer> histories = new HashMap<>();

        // 5. Pattern per match più flessibile dei ticket
        Pattern ticketPattern = Pattern.compile(
                "(?i)" + Pattern.quote(projectKey) + "-\\d+");

        // 6. Analisi commit per ogni coppia di tag
        for (int i = 1; i < tags.size(); i++) {
            Ref prevTag = tags.get(i-1);
            Ref currTag = tags.get(i);

            ObjectId prevId = Optional.ofNullable(prevTag.getPeeledObjectId())
                    .orElse(prevTag.getObjectId());
            ObjectId currId = Optional.ofNullable(currTag.getPeeledObjectId())
                    .orElse(currTag.getObjectId());

            try (RevWalk revWalk = new RevWalk(repository)) {
                Iterable<RevCommit> commits = git.log()
                        .addRange(prevId, currId)
                        .call();

                for (RevCommit commit : commits) {
                    processCommit(commit, repository, git, bugTickets,
                            ticketPattern, projectKey, buggyMethods,
                            churnMap, elseAddMap, elseDelMap, condMap,
                            addList, delList, churnList, authorMap, histories);
                }
            }
        }

        // 7. Calcolo metriche aggregate
        Map<String, Double> avgAdd = calculateAverages(addList);
        Map<String, Integer> maxAdd = calculateMaxValues(addList);
        Map<String, Double> avgDel = calculateAverages(delList);
        Map<String, Integer> maxDel = calculateMaxValues(delList);
        Map<String, Double> avgCh = calculateAverages(churnList);
        Map<String, Integer> maxCh = calculateMaxValues(churnList);

        git.close();
        repository.close();

        return new BuggyInfo(
                buggyMethods, churnMap, elseAddMap, elseDelMap, condMap,
                histories, calculateAuthorCounts(authorMap),
                avgAdd, maxAdd, avgDel, maxDel, avgCh, maxCh
        );
    }


    private static void processCommit(RevCommit commit, Repository repository, Git git,
                                      List<JiraTicket> bugTickets, Pattern ticketPattern,
                                      String projectKey, Set<String> buggyMethods,
                                      Map<String, Integer> churnMap,
                                      Map<String, Integer> elseAddMap,
                                      Map<String, Integer> elseDelMap,
                                      Map<String, Integer> condMap,
                                      Map<String, List<Integer>> addList,
                                      Map<String, List<Integer>> delList,
                                      Map<String, List<Integer>> churnList,
                                      Map<String, Set<String>> authorMap,
                                      Map<String, Integer> histories) throws IOException {

        String commitMsg = commit.getFullMessage();
        Matcher m = ticketPattern.matcher(commitMsg);

        while (m.find()) {
            String ticketKey = m.group().toUpperCase(); // Normalizza il case
            boolean isBugFix = bugTickets.stream()
                    .anyMatch(t -> t.getKey().equalsIgnoreCase(ticketKey));

            if (isBugFix) {
                logger.debug("Processing bug-fix commit {} for ticket {}",
                        commit.name(), ticketKey);

                try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    df.setRepository(repository);
                    df.setDiffComparator(RawTextComparator.DEFAULT);
                    df.setDetectRenames(true);

                    RevCommit[] parents = commit.getParents();
                    if (parents == null || parents.length == 0) continue;

                    for (DiffEntry diff : df.scan(parents[0], commit)) {
                        if (shouldSkipDiffEntry(diff)) continue;

                        processJavaFileDiff(diff, repository, commit, ticketKey,
                                buggyMethods, churnMap, elseAddMap,
                                elseDelMap, condMap, addList, delList,
                                churnList, authorMap, histories);
                    }
                }
            }
        }
    }

    private static boolean shouldSkipDiffEntry(DiffEntry diff) {
        if (diff.getChangeType() == DiffEntry.ChangeType.ADD) {
            // Potresti voler processare anche i file nuovi
            return false;
        }
        return diff.getChangeType() == DiffEntry.ChangeType.RENAME ||
                !diff.getNewPath().endsWith(".java") ||
                diff.getNewPath().contains("test/") ||
                diff.getNewPath().contains("Test.java");
    }

    private static void processJavaFileDiff(DiffEntry diff, Repository repository,
                                            RevCommit commit, String ticketKey,
                                            Set<String> buggyMethods,
                                            Map<String, Integer> churnMap,
                                            Map<String, Integer> elseAddMap,
                                            Map<String, Integer> elseDelMap,
                                            Map<String, Integer> condMap,
                                            Map<String, List<Integer>> addList,
                                            Map<String, List<Integer>> delList,
                                            Map<String, List<Integer>> churnList,
                                            Map<String, Set<String>> authorMap,
                                            Map<String, Integer> histories) throws IOException {

        String newSrc = getFileContent(repository, diff.getNewId().toObjectId());
        String oldSrc = getFileContent(repository, diff.getOldId().toObjectId());

        CompilationUnit newCu = parseSafely(newSrc);
        CompilationUnit oldCu = oldSrc.isEmpty() ? new CompilationUnit()
                : parseSafely(oldSrc);

        List<String> oldLines = oldSrc.isEmpty() ? List.of()
                : Arrays.asList(oldSrc.split("\\r?\\n"));
        List<String> newLines = Arrays.asList(newSrc.split("\\r?\\n"));

        String author = commit.getAuthorIdent().getName();
        String fileName = Paths.get(diff.getNewPath()).getFileName().toString();

        for (MethodDeclaration newMd : newCu.findAll(MethodDeclaration.class)) {
            newMd.getRange().ifPresent(newRange -> {
                String methodSignature = newMd.getDeclarationAsString(false, false, false);
                String methodId = fileName + "#" + methodSignature;

                Optional<MethodDeclaration> oldMdOpt = findMatchingMethod(oldCu, methodSignature);

                if (oldMdOpt.isPresent()) {
                    MethodDeclaration oldMd = oldMdOpt.get();
                    oldMd.getRange().ifPresent(oldRange -> {
                        processMethodChanges(diff, repository, newRange, oldRange,
                                methodId, author, newLines, oldLines,
                                buggyMethods, ticketKey, commit,
                                churnMap, elseAddMap, elseDelMap,
                                condMap, addList, delList, churnList,
                                authorMap, histories, newMd, oldMd);
                    });
                }
            });
        }
    }

    private static Optional<MethodDeclaration> findMatchingMethod(CompilationUnit cu,
                                                                  String signature) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(md -> md.getDeclarationAsString(false, false, false).equals(signature))
                .findFirst();
    }

    private static void processMethodChanges(DiffEntry diff, Repository repository,
                                             Range newRange, Range oldRange,
                                             String methodId, String author,
                                             List<String> newLines, List<String> oldLines,
                                             Set<String> buggyMethods, String ticketKey,
                                             RevCommit commit,
                                             Map<String, Integer> churnMap,
                                             Map<String, Integer> elseAddMap,
                                             Map<String, Integer> elseDelMap,
                                             Map<String, Integer> condMap,
                                             Map<String, List<Integer>> addList,
                                             Map<String, List<Integer>> delList,
                                             Map<String, List<Integer>> churnList,
                                             Map<String, Set<String>> authorMap,
                                             Map<String, Integer> histories,
                                             MethodDeclaration newMd, MethodDeclaration oldMd) {
        try {
            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
            df.setRepository(repository);

            for (Edit edit : df.toFileHeader(diff).toEditList()) {
                if (isEditInMethodRange(edit, newRange)) {
                    processEdit(edit, methodId, author, newLines, oldLines,
                            churnMap, elseAddMap, elseDelMap, condMap,
                            addList, delList, churnList, authorMap, histories);
                }
            }

            // Verifica se il metodo è stato modificato in un bug-fix
            if (isBuggyMethodChange(newMd, oldMd)) {
                buggyMethods.add(methodId);
                logger.debug("Identified buggy method: {} in commit {}",
                        methodId, commit.name());
            }
        } catch (IOException e) {
            logger.error("Error processing method changes", e);
        }
    }

    private static boolean isEditInMethodRange(Edit edit, Range methodRange) {
        return edit.getBeginB() <= methodRange.end.line &&
                edit.getEndB() >= methodRange.begin.line;
    }

    private static void processEdit(Edit edit, String methodId, String author,
                                    List<String> newLines, List<String> oldLines,
                                    Map<String, Integer> churnMap,
                                    Map<String, Integer> elseAddMap,
                                    Map<String, Integer> elseDelMap,
                                    Map<String, Integer> condMap,
                                    Map<String, List<Integer>> addList,
                                    Map<String, List<Integer>> delList,
                                    Map<String, List<Integer>> churnList,
                                    Map<String, Set<String>> authorMap,
                                    Map<String, Integer> histories) {
        int added = edit.getEndB() - edit.getBeginB();
        int deleted = edit.getEndA() - edit.getBeginA();
        int churn = added + deleted;

        // Aggiorna le metriche
        churnMap.merge(methodId, churn, Integer::sum);
        addList.computeIfAbsent(methodId, k -> new ArrayList<>()).add(added);
        delList.computeIfAbsent(methodId, k -> new ArrayList<>()).add(deleted);
        churnList.computeIfAbsent(methodId, k -> new ArrayList<>()).add(churn);
        authorMap.computeIfAbsent(methodId, k -> new HashSet<>()).add(author);
        histories.merge(methodId, 1, Integer::sum);

        // Analisi delle condizioni
        analyzeConditions(edit, newLines, oldLines, methodId,
                elseAddMap, elseDelMap, condMap);
    }

    private static void analyzeConditions(Edit edit, List<String> newLines,
                                          List<String> oldLines, String methodId,
                                          Map<String, Integer> elseAddMap,
                                          Map<String, Integer> elseDelMap,
                                          Map<String, Integer> condMap) {
        // Analizza le nuove linee
        for (int ln = edit.getBeginB(); ln < edit.getEndB(); ln++) {
            if (ln < newLines.size()) {
                String line = newLines.get(ln);
                if (line.contains("else")) elseAddMap.merge(methodId, 1, Integer::sum);
                if (line.contains("if") || line.contains("case") || line.contains("switch")) {
                    condMap.merge(methodId, 1, Integer::sum);
                }
            }
        }

        // Analizza le vecchie linee
        for (int ln = edit.getBeginA(); ln < edit.getEndA(); ln++) {
            if (ln < oldLines.size()) {
                String line = oldLines.get(ln);
                if (line.contains("else")) elseDelMap.merge(methodId, 1, Integer::sum);
            }
        }
    }

    private static boolean isBuggyMethodChange(MethodDeclaration newMd, MethodDeclaration oldMd) {
        String oldBody = normalize(oldMd.getBody().map(Object::toString).orElse(""));
        String newBody = normalize(newMd.getBody().map(Object::toString).orElse(""));
        return !oldBody.equals(newBody);
    }

    private static String getFileContent(Repository repo, ObjectId objectId) throws IOException {
        if (objectId.equals(ObjectId.zeroId())) {
            return ""; // Restituisci stringa vuota per file nuovi
        }
        return new String(repo.open(objectId).getBytes(), UTF_8);
    }

    private static CompilationUnit parseSafely(String content) {
        try {
            return StaticJavaParser.parse(content);
        } catch (Exception e) {
            logger.warn("Error parsing Java file, returning empty compilation unit");
            return new CompilationUnit();
        }
    }

    private static Map<String, Double> calculateAverages(Map<String, List<Integer>> data) {
        return data.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().mapToInt(i -> i).average().orElse(0.0)
                ));
    }

    private static Map<String, Integer> calculateMaxValues(Map<String, List<Integer>> data) {
        return data.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().mapToInt(i -> i).max().orElse(0)
                ));
    }

    private static Map<String, Integer> calculateAuthorCounts(Map<String, Set<String>> authorMap) {
        return authorMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size())
                );
    }

    private static void processBuggyMethods(DiffEntry diff,
                                            Repository repo,
                                            RevCommit commit,
                                            Set<String> buggyMethods,
                                            Map<String, String> buggyMethodsWithContext,
                                            String ticketKey,
                                            Map<String, Integer> churnMap,
                                            Map<String, Integer> elseAddMap,
                                            Map<String, Integer> elseDelMap,
                                            Map<String, Integer> condMap,
                                            Map<String, List<Integer>> addList,
                                            Map<String, List<Integer>> delList,
                                            Map<String, List<Integer>> churnList,
                                            Map<String, Set<String>> authorMap,
                                            Map<String, Integer> histories) throws Exception {
        String newSrc = new String(repo.open(diff.getNewId().toObjectId()).getBytes(), UTF_8);
        String oldSrc = diff.getOldId().equals(ObjectId.zeroId()) ? "" :
                new String(repo.open(diff.getOldId().toObjectId()).getBytes(), UTF_8);

        CompilationUnit newCu = StaticJavaParser.parse(newSrc);
        CompilationUnit oldCu = oldSrc.isEmpty() ? new CompilationUnit() : StaticJavaParser.parse(oldSrc);

        List<String> oldLines = oldSrc.isEmpty() ? List.of() : List.of(oldSrc.split("\\r?\\n", -1));
        List<String> newLines = List.of(newSrc.split("\\r?\\n", -1));

        String author = commit.getAuthorIdent().getName();

        for (MethodDeclaration newMd : newCu.findAll(MethodDeclaration.class)) {
            newMd.getRange().ifPresent(newRange -> {
                String methodId = Paths.get(diff.getNewPath()).getFileName() +
                        "#" + newMd.getDeclarationAsString(false, false, false);

                // Trova il metodo corrispondente nella vecchia versione
                Optional<MethodDeclaration> oldMdOpt = oldCu.findAll(MethodDeclaration.class)
                        .stream()
                        .filter(md -> md.getDeclarationAsString(false, false, false)
                                .equals(newMd.getDeclarationAsString(false, false, false)))
                        .findFirst();

                // Calcola churn e metriche
                int added = 0, deleted = 0;
                if (oldMdOpt.isPresent()) {
                    MethodDeclaration oldMd = oldMdOpt.get();
                    oldMd.getRange().ifPresent(oldRange -> {
                        // Calcola modifiche
                        try {
                            for (Edit edit : new DiffFormatter(DisabledOutputStream.INSTANCE)
                                    .toFileHeader(diff).toEditList()) {
                                if (edit.getBeginB() + 1 <= newRange.end.line &&
                                        edit.getEndB() >= newRange.begin.line) {

                                    int delta = (edit.getEndB() - edit.getBeginB()) +
                                            (edit.getEndA() - edit.getBeginA());
                                    churnMap.merge(methodId, delta, Integer::sum);
                                    addList.computeIfAbsent(methodId, k -> new ArrayList<>())
                                            .add(edit.getEndB() - edit.getBeginB());
                                    delList.computeIfAbsent(methodId, k -> new ArrayList<>())
                                            .add(edit.getEndA() - edit.getBeginA());
                                    churnList.computeIfAbsent(methodId, k -> new ArrayList<>())
                                            .add(delta);
                                    authorMap.computeIfAbsent(methodId, k -> new HashSet<>())
                                            .add(author);
                                    histories.merge(methodId, 1, Integer::sum);

                                    // Analisi delle condizioni
                                    for (int ln = edit.getBeginB() + 1; ln <= edit.getEndB(); ln++) {
                                        String line = newLines.get(ln - 1);
                                        if (line.contains("else")) elseAddMap.merge(methodId, 1, Integer::sum);
                                        if (line.contains("if") || line.contains("case")) condMap.merge(methodId, 1, Integer::sum);
                                    }
                                    for (int ln = edit.getBeginA() + 1; ln <= edit.getEndA(); ln++) {
                                        if (oldLines.size() > ln - 1 && oldLines.get(ln - 1).contains("else")) {
                                            elseDelMap.merge(methodId, 1, Integer::sum);
                                        }
                                    }
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        // Verifica se è un bug-fix
                        String oldBody = oldMd.getBody().map(b -> normalize(b.toString())).orElse("");
                        String newBody = newMd.getBody().map(b -> normalize(b.toString())).orElse("");

                        if (!oldBody.equals(newBody)) {
                            buggyMethods.add(methodId);
                            buggyMethodsWithContext.put(methodId,
                                    String.format("%s (JIRA: %s, Commit: %s)",
                                            methodId, ticketKey, commit.name().substring(0, 7)));
                        }
                    });
                }
            });
        }
    }

    private static String normalize(String body) {
        if (body == null) return "";

        // 1. Rimuovi commenti
        String noComments = body.replaceAll("(?s)/\\*.*?\\*/", "")  // commenti multilinea
                .replaceAll("//.*", "");             // commenti singola linea

        // 2. Normalizza spazi e newline
        String normalized = noComments.replaceAll("\\s+", " ")
                .trim();

        // 3. Rimuovi spazi vicino a parentesi e operatori
        normalized = normalized.replaceAll("\\s*([{}();=+\\-*/])\\s*", "$1");

        return normalized;
    }

    private static Map<String,String> extractMethods(Repository repo, ObjectId commitId) throws Exception {
        Map<String,String> map = new HashMap<>();
        try (RevWalk rw = new RevWalk(repo)) {
            RevCommit commit = rw.parseCommit(commitId);
            RevTree tree     = commit.getTree();
            try (TreeWalk tw = new TreeWalk(repo)) {
                tw.addTree(tree); tw.setRecursive(true);
                while (tw.next()) {
                    String path = tw.getPathString();
                    if (!path.endsWith(".java")) continue;
                    byte[] bytes = repo.open(tw.getObjectId(0)).getBytes();
                    String src   = new String(bytes, UTF_8);
                    CompilationUnit cu = StaticJavaParser.parse(src);
                    for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
                        String id = new File(path).getName()
                                + "#" + md.getDeclarationAsString(false,false,false);
                        md.getAllContainedComments().forEach(Comment::remove);
                        String body = md.getBody().map(b->b.toString().trim()).orElse("");
                        map.put(id, body);
                    }
                }
            }
        }
        return map;
    }

    private static AbstractTreeIterator prepareTree(Repository repo, ObjectId commitId) throws Exception {
        RevTree tree;
        try (RevWalk rw = new RevWalk(repo)) {
            tree = rw.parseCommit(commitId).getTree();
        }
        CanonicalTreeParser p = new CanonicalTreeParser();
        try (var reader = repo.newObjectReader()) { p.reset(reader, tree.getId()); }
        return p;
    }

    private static BuggyInfo emptyInfo() {
        return new BuggyInfo(
                new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>()
        );
    }
}
