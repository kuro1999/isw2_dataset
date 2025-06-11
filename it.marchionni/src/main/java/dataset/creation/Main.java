package dataset.creation;

import dataset.creation.fetcher.BookkeeperFetcher;
import dataset.creation.fetcher.model.JiraTicket;
import dataset.creation.fetcher.model.JiraVersion;
import dataset.creation.fetcher.model.Release;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.util.List;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        logger.info("Avvio del processo di creazione dataset");

        /* 0. credenziali JIRA se private */
        String jiraUser = System.getenv("JIRA_USER");
        String jiraPass = System.getenv("JIRA_PASS");
        logger.info("Credenziali JIRA recuperate (user={}, pass={})", jiraUser!=null, jiraPass!=null);

        /* JSON pretty-print */
        Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));

        BookkeeperFetcher fetcher = new BookkeeperFetcher();

        /* ------- 1. versioni ------- */
        logger.info("Fase 1: recupero di tutte le versioni da JIRA...");
        List<JiraVersion> versions = fetcher.fetchJiraVersions(jiraUser, jiraPass);
        logger.info("Trovate {} versioni", versions.size());

        logger.info("Seleziono il 34% più vecchio delle versioni...");
        List<JiraVersion> oldest34 = fetcher.selectOldest34Percent(versions);
        logger.info("Selezionate {} versioni (il 34% più vecchio)", oldest34.size());

        /* ------- 2. ticket ------- */
        logger.info("Fase 2: recupero di tutti i ticket con fixedVersion...");
        List<JiraTicket> tickets = fetcher.fetchFixedJiraTickets(jiraUser, jiraPass);
        logger.info("Trovati {} ticket con fixedVersion", tickets.size());

        /* ------- 3. tag GitHub ------- */
        logger.info("Fase 3: recupero dei tag GitHub per le versioni selezionate...");
        List<Release> releasesSelected = fetcher.fetchReleasesForVersions(oldest34);
        logger.info("Recuperati {} release da GitHub", releasesSelected.size());

        /* ------- 5. serializza ------- */
        logger.info("Fase 5: serializzazione dei risultati in JSON...");
        jsonb.toJson(versions,         new FileWriter("all_versions.json"));
        jsonb.toJson(oldest34,         new FileWriter("oldest_versions.json"));
        jsonb.toJson(tickets,          new FileWriter("jira_tickets.json"));
        jsonb.toJson(releasesSelected, new FileWriter("releases_with_tags.json"));
        logger.info("Tutti i file JSON sono stati scritti correttamente.");

        System.out.println("✓ files JSON scritti.");
    }
}
