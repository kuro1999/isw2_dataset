package dataset.creation.features;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;;
import dataset.creation.fetcher.model.JiraTicket;
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
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;
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

import dataset.creation.features.metrics.StructuralChangeMetrics;
import dataset.creation.features.metrics.ComplexityMetrics;
import dataset.creation.features.metrics.ElseMetrics;
import dataset.creation.features.metrics.AddDeleteMetrics;
import dataset.creation.features.metrics.MethodMetrics;

/**
 * Estende il calcolo "buggy" usando il diff tra release consecutive,
 * senza DTO intermedi né PrettyPrinter, rimuovendo i commenti via AST.
 */
public class BuggyMethodExtractor {
    private static final Logger logger = LoggerFactory.getLogger(BuggyMethodExtractor.class);
    private static final String NEWLINE_REGEX = "\\r?\\n";

    private static String normalizeId(String rawId) {
        return rawId.replaceAll("\\s+", "");
    }
    public static BuggyInfo computeOrLoad(
            File repoDir,
            List<JiraTicket> tickets,
            String projectKey,
            Path cacheDir
    ) throws Exception {
        String cacheFileName = projectKey + "_buggy_info_cache.json";
        File cache = cacheDir.resolve(cacheFileName).toFile();

        if (cache.exists()) {
            logger.info("🟡 Cache esistente, caricamento da: {}", cache);
            try (Reader r = new FileReader(cache);
                 Jsonb jsonb = JsonbBuilder.create()) {
                return jsonb.fromJson(r, BuggyInfo.class);
            }
        }

        logger.info("🔵 Nessuna cache trovata, computo da zero");
        BuggyInfo info = computeBuggyMethods(repoDir, tickets, projectKey);

        try (Writer w = new FileWriter(cache);
             Jsonb jsonb = JsonbBuilder.create()) {
            jsonb.toJson(info, w);
            logger.info("✅ Cache salvata in {}", cache.getPath());
        }
        return info;
    }

    private static BuggyInfo computeBuggyMethods(File repoDir,
                                                 List<JiraTicket> tickets,
                                                 String projectKey) throws Exception {
        // 1. Filtra i bug-fix tickets
        List<JiraTicket> bugTickets = tickets.stream()
                .filter(t -> "bug".equalsIgnoreCase(t.getIssueType()))
                .filter(t -> "Fixed".equalsIgnoreCase(t.getResolution()))
                .filter(t -> Set.of("Closed","Resolved").contains(t.getStatus()))
                .filter(t -> t.getKey()!=null && !t.getKey().isEmpty())
                .collect(Collectors.toList());

        logger.info("Ticket di bug trovati: {}", bugTickets.size());
        if (bugTickets.isEmpty()) {
            logger.warn("Nessun ticket di bug valido trovato!");
            return emptyInfo();
        }

        // 2. Apri repository
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir,".git"))
                .readEnvironment().findGitDir().build();
        Git git = new Git(repository);

        // 3. Leggi e ordina tag per data
        List<Ref> tags = git.tagList().call();
        if (tags.isEmpty()) {
            tags = git.lsRemote()
                    .setRemote("origin")
                    .setTags(true).setHeads(false).call()
                    .stream()
                    .filter(r->r.getName().startsWith("refs/tags/"))
                    .collect(Collectors.toList());
        }
        tags.sort(Comparator.comparing(ref -> {
            try (RevWalk rw = new RevWalk(repository)) {
                ObjectId id = Optional.ofNullable(ref.getPeeledObjectId())
                        .orElse(ref.getObjectId());
                return rw.parseCommit(id).getCommitTime();
            } catch (Exception e) {
                logger.error("Errore ordinamento tag",e);
                return 0;
            }
        }));

        // 4. Strutture dati per raccolta raw
        Set<String> buggyMethods   = new HashSet<>();
        Map<String,Integer> churnMap    = new HashMap<>();
        Map<String,Integer> elseAddMap  = new HashMap<>();
        Map<String,Integer> elseDelMap  = new HashMap<>();
        Map<String,Integer> condMap     = new HashMap<>();
        Map<String,List<Integer>> addList   = new HashMap<>();
        Map<String,List<Integer>> delList   = new HashMap<>();
        Map<String,List<Integer>> churnList = new HashMap<>();
        Map<String,Set<String>> authorMap    = new HashMap<>();
        Map<String,Integer> histories    = new HashMap<>();

        Pattern ticketPattern = Pattern.compile("(?i)"+Pattern.quote(projectKey)+"-\\d+");

        // 5. Scorri le coppie di tag e processa commit
        for (int i=1; i<tags.size(); i++) {
            Ref prev = tags.get(i-1), curr = tags.get(i);
            ObjectId prevId = Optional.ofNullable(prev.getPeeledObjectId())
                    .orElse(prev.getObjectId());
            ObjectId currId = Optional.ofNullable(curr.getPeeledObjectId())
                    .orElse(curr.getObjectId());

            try (RevWalk rw = new RevWalk(repository)) {
                Iterable<RevCommit> commits = git.log()
                        .addRange(prevId, currId).call();
                for (RevCommit c: commits) {
                    processCommit(c, repository, git,
                            bugTickets, ticketPattern, projectKey,
                            buggyMethods,
                            churnMap, elseAddMap, elseDelMap, condMap,
                            addList, delList, churnList, authorMap, histories);
                }
            }
        }

        // 6. Calcola metriche aggregate
        Map<String,Double> avgAdd = calculateAverages(addList);
        Map<String,Integer> maxAdd = calculateMaxValues(addList);
        Map<String,Double> avgDel = calculateAverages(delList);
        Map<String,Integer> maxDel = calculateMaxValues(delList);
        Map<String,Double> avgCh  = calculateAverages(churnList);
        Map<String,Integer> maxCh  = calculateMaxValues(churnList);
        Map<String,Integer> authorCounts = calculateAuthorCounts(authorMap);

        git.close();
        repository.close();

        // 7. Costruisci la mappa di MethodMetrics
        Map<String, MethodMetrics> metricsByMethod = new HashMap<>();
        Set<String> allIds = new HashSet<>();
        allIds.addAll(churnMap.keySet());
        allIds.addAll(elseAddMap.keySet());
        allIds.addAll(elseDelMap.keySet());
        allIds.addAll(condMap.keySet());
        allIds.addAll(histories.keySet());
        allIds.addAll(authorCounts.keySet());
        allIds.addAll(avgAdd.keySet());
        allIds.addAll(maxAdd.keySet());
        allIds.addAll(avgDel.keySet());
        allIds.addAll(maxDel.keySet());
        allIds.addAll(avgCh.keySet());
        allIds.addAll(maxCh.keySet());

        for (String rawId : allIds) {
            String id = normalizeId(rawId);
            StructuralChangeMetrics structural =
                    new StructuralChangeMetrics(
                            churnMap.getOrDefault(rawId,0),
                            avgCh.getOrDefault(rawId,0.0),
                            maxCh.getOrDefault(rawId,0),
                            condMap.getOrDefault(rawId,0)
                    );
            ComplexityMetrics complexity =
                    new ComplexityMetrics(
                            histories.getOrDefault(rawId,0),
                            authorCounts.getOrDefault(rawId,0)
                    );
            ElseMetrics elseMetrics =
                    new ElseMetrics(
                            elseAddMap.getOrDefault(rawId,0),
                            elseDelMap.getOrDefault(rawId,0)
                    );
            AddDeleteMetrics addDelete =
                    new AddDeleteMetrics(
                            avgAdd.getOrDefault(rawId,0.0),
                            maxAdd.getOrDefault(rawId,0),
                            avgDel.getOrDefault(rawId,0.0),
                            maxDel.getOrDefault(rawId,0)
                    );
            metricsByMethod.put(id,
                    new MethodMetrics(structural, complexity, elseMetrics, addDelete)
            );
        }
        Set<String> normalizedBuggy = buggyMethods.stream()
                .map(BuggyMethodExtractor::normalizeId)
                .collect(Collectors.toSet());
        return new BuggyInfo(buggyMethods, metricsByMethod);
    }

    private static BuggyInfo emptyInfo() {
        return new BuggyInfo(
                Collections.emptySet(),
                Collections.emptyMap()
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
                : Arrays.asList(oldSrc.split(NEWLINE_REGEX));
        List<String> newLines = Arrays.asList(newSrc.split(NEWLINE_REGEX));

        String author = commit.getAuthorIdent().getName();
        String fileName = Paths.get(diff.getNewPath()).getFileName().toString();

        for (MethodDeclaration newMd : newCu.findAll(MethodDeclaration.class)) {
            newMd.getRange().ifPresent(newRange -> {
                String methodSignature = newMd.getDeclarationAsString(false, false, false);
                String rawId    = fileName + "#" + methodSignature;
                String methodId = normalizeId(rawId);

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

    private static void analyzeConditions(
            Edit edit,
            List<String> newLines,
            List<String> oldLines,
            String methodId,
            Map<String, Integer> elseAddMap,
            Map<String, Integer> elseDelMap,
            Map<String, Integer> condMap) {

        analyzeNewConditions(edit.getBeginB(), edit.getEndB(), newLines, methodId, elseAddMap, condMap);
        analyzeOldConditions(edit.getBeginA(), edit.getEndA(), oldLines, methodId, elseDelMap);
    }

    private static void analyzeNewConditions(
            int start, int end,
            List<String> lines,
            String methodId,
            Map<String, Integer> elseMap,
            Map<String, Integer> condMap) {

        for (int ln = start; ln < end && ln < lines.size(); ln++) {
            String line = lines.get(ln);
            if (line.contains("else")) {
                elseMap.merge(methodId, 1, Integer::sum);
            }
            if (line.contains("if") || line.contains("case") || line.contains("switch")) {
                condMap.merge(methodId, 1, Integer::sum);
            }
        }
    }

    private static void analyzeOldConditions(
            int start, int end,
            List<String> lines,
            String methodId,
            Map<String, Integer> elseMap) {

        for (int ln = start; ln < end && ln < lines.size(); ln++) {
            if (lines.get(ln).contains("else")) {
                elseMap.merge(methodId, 1, Integer::sum);
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
}