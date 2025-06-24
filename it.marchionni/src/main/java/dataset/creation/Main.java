package dataset.creation;

import dataset.creation.fetcher.BookkeeperFetcher;
import dataset.creation.fetcher.model.JiraTicket;
import dataset.creation.features.FeatureExtractor;
import dataset.creation.features.BuggyMethodExtractor;
import dataset.creation.features.BuggyMethodExtractor.BuggyInfo;
import dataset.creation.features.CsvGenerator;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        logger.info("Avvio del processo di esportazione e creazione del dataset");

        /* ------------ 1. Ticket JIRA (load o download) ------------ */
        String jsonFileName = "bookkeeper_jira_tickets.json";
        Path   jsonPath     = Paths.get(jsonFileName);

        Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));
        List<JiraTicket> tickets;

        if (Files.exists(jsonPath)) {
            logger.info("Trovato {}: carico i ticket da file", jsonFileName);
            try (Reader r = Files.newBufferedReader(jsonPath)) {
                Type listType = new ArrayList<JiraTicket>(){}.getClass().getGenericSuperclass();
                tickets = jsonb.fromJson(r, listType);
            }
        } else {
            logger.info("File {} non trovato: scarico da JIRA…", jsonFileName);
            String jiraUser = System.getenv("JIRA_USER");
            String jiraPass = System.getenv("JIRA_PASS");

            BookkeeperFetcher fetcher = new BookkeeperFetcher();
            tickets = fetcher.fetchAllJiraTickets(jiraUser, jiraPass);
            fetcher.writeTicketsToJsonFile(tickets, jsonFileName);
        }
        logger.info("Caricati {} ticket", tickets.size());

        /* ------------ 2. Repo locale BookKeeper ------------ */
        File bkRepo = new File("/home/edo/isw2/bookkeeper_isw2");
        if (!bkRepo.isDirectory()) {
            logger.error("Repo BookKeeper non trovato: {}", bkRepo.getPath());
            return;
        }

        /* ------------ 3. Estrazione feature sorgenti ------------ */
        logger.info("Estraggo feature dai sorgenti BookKeeper…");
        FeatureExtractor extractor = new FeatureExtractor();
        Map<File, Map<String, FeatureExtractor.MethodFeatures>> allFeatures = new HashMap<>();

        try (Stream<Path> paths = Files.walk(bkRepo.toPath())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .map(Path::toFile)
                    .forEach(file -> {
                        try { allFeatures.put(file, extractor.extractFromFile(file)); }
                        catch (Exception ex) { logger.error("Parse {}: {}", file, ex.getMessage()); }
                    });
        }
        logger.info("Feature estratte per {} file", allFeatures.size());

        /* ------------ 4. Metodi buggy + priority ------------ */
        logger.info("Identifico metodi buggy dai commit di fix…");
        BuggyInfo bugInfo = BuggyMethodExtractor.computeBuggyMethods(bkRepo, tickets);
        logger.info("Trovati {} metodi buggy", bugInfo.buggyMethods.size());

        /* ------------ 5. CSV finale ------------ */
        String csvFile = "dataset_bookkeeper.csv";
        new CsvGenerator(1).generateCsv(allFeatures, bugInfo, csvFile);
        logger.info("✨ Dataset creato: {}", csvFile);

        /* ------------ 6. Statistica code-smells ------------ */
        int totalSmells = allFeatures.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(f -> f.codeSmells)
                .sum();
        logger.info("⚠️  Code smells totali rilevati: {}", totalSmells);
    }
}
