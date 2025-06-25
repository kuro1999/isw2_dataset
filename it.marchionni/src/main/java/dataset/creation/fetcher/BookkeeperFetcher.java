package dataset.creation.fetcher;

import dataset.creation.fetcher.model.JiraTicket;
import dataset.creation.fetcher.model.JiraVersion;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BookkeeperFetcher {

    private static final String JIRA_SEARCH_API     = "https://issues.apache.org/jira/rest/api/2/search";
    private static final String JIRA_JQL_ALL        = "project = BOOKKEEPER ORDER BY created ASC";
    private static final String JIRA_VERSIONS_API   = "https://issues.apache.org/jira/rest/api/2/project/BOOKKEEPER/versions";

    private final OkHttpClient client = new OkHttpClient();
    private final Jsonb        jsonb;

    public BookkeeperFetcher() {
        this.jsonb = JsonbBuilder.create(
                new JsonbConfig().withFormatting(true)
        );
    }

    /**
     * Recupera tutti i ticket JIRA (paginati) per il progetto BOOKKEEPER.
     */
    public List<JiraTicket> fetchAllJiraTickets(String user, String pwd) throws IOException {
        List<JiraTicket> tickets = new ArrayList<>();
        int startAt = 0, pageSize = 500, total;

        do {
            HttpUrl url = HttpUrl.parse(JIRA_SEARCH_API).newBuilder()
                    .addQueryParameter("jql",        JIRA_JQL_ALL)
                    .addQueryParameter("fields",     "*all")
                    .addQueryParameter("startAt",    String.valueOf(startAt))
                    .addQueryParameter("maxResults", String.valueOf(pageSize))
                    .build();

            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json");
            if (user != null && pwd != null) {
                rb.header("Authorization", Credentials.basic(user, pwd));
            }

            try (Response resp = client.newCall(rb.build()).execute()) {
                if (!resp.isSuccessful()) {
                    throw new IOException("JIRA search API error: HTTP " + resp.code());
                }

                String body = resp.body().string();
                JiraSearchResponse sr = jsonb.fromJson(body, JiraSearchResponse.class);
                total = sr.total;
                for (JiraSearchIssue si : sr.issues) {
                    tickets.add(mapIssue(si));
                }
            }

            startAt += pageSize;
        } while (startAt < total);

        return tickets;
    }

    /**
     * Scrive su file la lista di JiraTicket in JSON formattato.
     */
    public void writeTicketsToJsonFile(List<JiraTicket> tickets, String filePath) throws IOException {
        Files.writeString(Paths.get(filePath), jsonb.toJson(tickets));
    }

    /**
     * Recupera da JIRA la lista delle versioni del progetto BOOKKEEPER.
     * Ritorna la lista di name (es. "4.0.0", "4.1.2", ...).
     */
    public List<String> fetchProjectVersions(String user, String pwd) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(JIRA_VERSIONS_API)
                .header("Accept", "application/json");
        if (user != null && pwd != null) {
            rb.header("Authorization", Credentials.basic(user, pwd));
        }

        try (Response resp = client.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("JIRA versions API error: HTTP " + resp.code());
            }
            String body = resp.body().string();
            // Deserializziamo l'array JSON in List<JiraVersion>
            List<JiraVersion> versions = jsonb.fromJson(
                    body,
                    new ArrayList<JiraVersion>() {}.getClass().getGenericSuperclass()
            );
            // Ritorniamo solo il campo "name"
            return versions.stream()
                    .map(JiraVersion::getName)
                    .collect(Collectors.toList());
        }
    }

    // ------------------ supporto per mappatura search API ------------------

    private static JiraTicket mapIssue(JiraSearchIssue si) {
        JiraTicket t = new JiraTicket();
        Fields f   = si.fields;

        t.setKey(si.key);
        t.setSummary(f.summary);
        t.setDescription(f.description);
        t.setStatus(f.status      != null ? f.status.name      : null);
        t.setIssueType(f.issuetype != null ? f.issuetype.name : null);
        t.setPriority(f.priority    != null ? f.priority.name    : null);
        t.setReporter(f.reporter    != null ? f.reporter.displayName : null);
        t.setAssignee(f.assignee    != null ? f.assignee.displayName : null);
        t.setResolution(f.resolution!= null ? f.resolution.name : null);

        // (mappa qui eventuali date, fixVersions, etc. se ti servono)
        return t;
    }

    public static class JiraSearchResponse {
        public int total;
        public List<JiraSearchIssue> issues;
    }
    public static class JiraSearchIssue {
        public String key;
        public Fields fields;
    }
    public static class Fields {
        public String summary;
        public String description;
        public Status     status;
        public IssueType  issuetype;
        public Priority   priority;
        public User       reporter;
        public User       assignee;
        public Resolution resolution;
        // aggiungi altri campi se necessari
    }
    public static class Status     { public String name; }
    public static class IssueType  { public String name; }
    public static class Priority   { public String name; }
    public static class User       { public String displayName; }
    public static class Resolution { public String name; }
}
