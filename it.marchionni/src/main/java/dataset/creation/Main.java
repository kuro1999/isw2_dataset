package dataset.creation;

import dataset.creation.fetcher.BookkeeperFetcher;
import dataset.creation.fetcher.model.JiraTicket;
import dataset.creation.features.FeatureExtractor;
import dataset.creation.features.BuggyMethodExtractor;
import dataset.creation.features.CsvGenerator;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        logger.info("Avvio del processo di esportazione e creazione del dataset");

        // 1) JSON dei ticket JIRA
        String jsonFileName = "bookkeeper_jira_tickets.json";
        Path   jsonPath     = Paths.get(jsonFileName);

        // JSON-B per (de)serializzare la lista di ticket
        Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));

        // 2) Carica o scarica i ticket
        List<JiraTicket> tickets;
        if (Files.exists(jsonPath)) {
            logger.info("Trovato {}: carico i ticket da file", jsonFileName);
            try (Reader r = Files.newBufferedReader(jsonPath)) {
                Type listType = new ArrayList<JiraTicket>() { }
                        .getClass()
                        .getGenericSuperclass();
                tickets = jsonb.fromJson(r, listType);
            }
            logger.info("Caricati {} ticket da JSON", tickets.size());
        } else {
            logger.info("File {} non trovato: scarico i ticket da JIRA...", jsonFileName);
            String jiraUser = System.getenv("JIRA_USER");
            String jiraPass = System.getenv("JIRA_PASS");
            logger.info("Credenziali JIRA presenti: user={} pass={}", jiraUser != null, jiraPass != null);

            BookkeeperFetcher fetcher = new BookkeeperFetcher();
            long start = System.nanoTime();
            tickets = fetcher.fetchAllJiraTickets(jiraUser, jiraPass);
            double elapsedSec = (System.nanoTime() - start) / 1_000_000_000.0;
            logger.info("Download completato: {} ticket in {}s", tickets.size(), String.format("%.2f", elapsedSec));

            // salva su disco
            logger.info("Scrivo JSON su {}...", jsonFileName);
            fetcher.writeTicketsToJsonFile(tickets, jsonFileName);
        }

        // 3) Usa la tua copia locale di BookKeeper (senza riscaricare)
        File bkRepo = new File("/home/edo/isw2/bookkeeper_isw2");
        if (!bkRepo.exists() || !bkRepo.isDirectory()) {
            logger.error("Directory del repo BookKeeper non trovata: {}", bkRepo.getPath());
            System.err.println("Errore: directory del repo BookKeeper non trovata: " + bkRepo.getPath());
            return;
        } else {
            logger.info("Usando il repo BookKeeper locale: {}", bkRepo.getPath());
        }

        // 4) Estrai feature da tutti i .java del repo BookKeeper
        logger.info("Estraggo le feature dai sorgenti di BookKeeper…");
        FeatureExtractor extractor = new FeatureExtractor();
        Map<File, Map<String, FeatureExtractor.MethodFeatures>> allFeatures = new HashMap<>();

        try (Stream<Path> paths = Files.walk(bkRepo.toPath())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .forEach(file -> {
                        try {
                            allFeatures.put(file, extractor.extractFromFile(file));
                        } catch (Exception e) {
                            logger.error("Errore parsing {}: {}", file, e.getMessage());
                        }
                    });
        }
        logger.info("Feature estratte per {} file", allFeatures.size());

        // 5) Identifica i methodId buggy dai commit di fix
        logger.info("Identifico i metodi buggy dai commit di fix…");
        Set<String> buggyMethods = BuggyMethodExtractor.computeBuggyMethods(bkRepo, tickets);
        logger.info("Trovati {} metodi buggy", buggyMethods.size());

        // 6) Genera il CSV in formato “professore-style”
        String csvFile = "dataset_bookkeeper.csv";
        logger.info("Genero CSV su {} …", csvFile);
        int datasetVersion = 1;
        new CsvGenerator(datasetVersion)
                .generateCsv(allFeatures, buggyMethods, csvFile);

        logger.info("✨ Dataset creato: {}", csvFile);
    }
}
