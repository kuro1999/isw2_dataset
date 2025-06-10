package dataset.creation;

import dataset.creation.fetcher.BookkeeperFetcher;
import dataset.creation.fetcher.model.Release;
import dataset.creation.fetcher.model.JiraIssue;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        // Carica le credenziali da variabili d’ambiente
        String githubToken = System.getenv("GITHUB_TOKEN");
        String jiraUser    = System.getenv("JIRA_USER");
        String jiraPass    = System.getenv("JIRA_PASS");

        BookkeeperFetcher fetcher = new BookkeeperFetcher();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        try {
            // 1) Tutte le release
            List<Release> all = fetcher.fetchAllReleases(githubToken);
            // 2) Filtra il 34% più nuove
            List<Release> recent = fetcher.filterNewest34Percent(all);

            // 3) Per ogni release, prendi i ticket JIRA
            Map<String, List<JiraIssue>> map = new LinkedHashMap<>();
            for (Release r : recent) {
                List<JiraIssue> issues = fetcher.fetchIssuesForVersion(
                        r.getTagName(), jiraUser, jiraPass);
                map.put(r.getTagName(), issues);
            }

            // 4) Salva JSON
            try (FileOutputStream fos = new FileOutputStream("releases.json")) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(fos, recent);
            }
            try (FileOutputStream fos = new FileOutputStream("jira_issues.json")) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(fos, map);
            }

            System.out.printf("Estratte %d release e relativi ticket JIRA.%n", recent.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
