package dataset.creation;

import dataset.creation.fetcher.BookkeeperFetcher;
import dataset.creation.fetcher.JiraInjection;
import dataset.creation.fetcher.model.Release;
import dataset.creation.fetcher.model.JiraVersion;
import dataset.creation.fetcher.model.JiraTicket;

import dataset.creation.utils.InstantAdapter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // 1) Configuro JSON-B con l’adapter per Instant
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new InstantAdapter());
        Jsonb jsonb = JsonbBuilder.create(config);

        try {
            // --- PARTE 1: GitHub BookKeeper releases ---
            BookkeeperFetcher ghFetcher = new BookkeeperFetcher();
            List<Release> all = ghFetcher.fetchAllReleases();
            List<Release> recent = ghFetcher.filterNewest34Percent(all);


            // Serializzo in releases.json
            try (FileWriter fw = new FileWriter("releases.json")) {
                jsonb.toJson(recent, fw);
            }
            System.out.printf("Estratte %d release da GitHub e salvate in releases.json%n",
                    recent.size());

            // --- PARTE 2: JIRA BookKeeper tickets ---
            JiraInjection ji = new JiraInjection("BOOKKEEPER");
            ji.injectReleases();
            ji.pullIssues();
            ji.filterFixedNormally();

            List<JiraVersion> jiraVersions = ji.getReleases();
            List<JiraTicket>  jiraBugs     = ji.getFixedTickets();

            // Serializzo in jira_versions.json e jira_bugs.json
            try (FileWriter fw = new FileWriter("jira_versions.json")) {
                jsonb.toJson(jiraVersions, fw);
            }
            try (FileWriter fw = new FileWriter("jira_bugs.json")) {
                jsonb.toJson(jiraBugs, fw);
            }
            System.out.printf("Estratte %d release JIRA e %d ticket fixed, salvati in jira_*.json%n",
                    jiraVersions.size(), jiraBugs.size());

        } catch (IOException e) {
            System.err.println("Errore I/O: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
