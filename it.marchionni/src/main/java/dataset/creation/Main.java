package dataset.creation;

import dataset.creation.fetcher.BookkeeperFetcher;
import dataset.creation.fetcher.model.*;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import java.io.FileWriter;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        /* 0. credenziali JIRA se private */
        String jiraUser = System.getenv("JIRA_USER");
        String jiraPass = System.getenv("JIRA_PASS");

        /* JSON pretty-print */
        Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));

        BookkeeperFetcher fetcher = new BookkeeperFetcher();

        /* ------- 1. versioni ------- */
        List<JiraVersion> versions      = fetcher.fetchJiraVersions(jiraUser, jiraPass);
        List<JiraVersion> oldest34      = fetcher.selectOldest34Percent(versions);

        /* ------- 2. ticket ------- */
        List<JiraTicket> tickets        = fetcher.fetchFixedJiraTickets(jiraUser, jiraPass);

        /* ------- 3. tag GitHub (solo per le versioni selezionate) ------- */
        List<Release> releasesSelected  = fetcher.fetchReleasesForVersions(oldest34);

        /* ------- 4. serializza ------- */
        jsonb.toJson(versions,         new FileWriter("all_versions.json"));
        jsonb.toJson(oldest34,         new FileWriter("oldest_versions.json"));
        jsonb.toJson(tickets,          new FileWriter("jira_tickets.json"));
        jsonb.toJson(releasesSelected, new FileWriter("releases_with_tags.json"));

        System.out.println("✓ files JSON scritti.");
    }
}
