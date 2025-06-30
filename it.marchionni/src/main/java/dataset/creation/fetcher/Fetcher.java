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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Fetcher {
    private static final String JIRA_SEARCH_API   = "https://issues.apache.org/jira/rest/api/2/search";
    private static final String JIRA_VERSIONS_API = "https://issues.apache.org/jira/rest/api/2/project/%s/versions";

    private final OkHttpClient client;
    private final Jsonb jsonb;
    private final String jiraProject;

    public Fetcher(String jiraProject) {
        this.client = new OkHttpClient();
        this.jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));
        this.jiraProject = Objects.requireNonNull(jiraProject);
    }

    /**
     * Recupera tutti i ticket JIRA per il progetto configurato.
     */
    public List<JiraTicket> fetchAllJiraTickets(String user, String pwd) throws IOException {
        List<JiraTicket> tickets = new ArrayList<>();
        int startAt = 0, pageSize = 500, total;
        String jql = String.format("project = %s ORDER BY created ASC", jiraProject);

        do {
            HttpUrl url = HttpUrl.parse(JIRA_SEARCH_API).newBuilder()
                    .addQueryParameter("jql", jql)
                    .addQueryParameter("fields", "*all")
                    .addQueryParameter("startAt", String.valueOf(startAt))
                    .addQueryParameter("maxResults", String.valueOf(pageSize))
                    .build();

            Request request = buildAuthenticatedRequest(user, pwd, url).build();

            try (Response resp = client.newCall(request).execute()) {
                validateResponse(resp);
                JiraSearchResponse sr = jsonb.fromJson(resp.body().string(), JiraSearchResponse.class);
                total = sr.total;
                sr.issues.forEach(issue -> tickets.add(mapIssue(issue)));
            }
            startAt += pageSize;
        } while (startAt < total);

        return tickets;
    }

    /**
     * Scrive i ticket su file in formato JSON.
     */
    public void writeTicketsToJsonFile(List<JiraTicket> tickets, Path filePath) throws IOException {
        Files.writeString(filePath, jsonb.toJson(tickets));
    }

    /**
     * Recupera tutte le versioni del progetto configurato.
     */
    public List<JiraVersion> fetchProjectVersions(String user, String pwd) throws IOException {
        String apiUrl = String.format(JIRA_VERSIONS_API, jiraProject);
        Request request = buildAuthenticatedRequest(user, pwd, HttpUrl.parse(apiUrl)).build();

        try (Response resp = client.newCall(request).execute()) {
            validateResponse(resp);
            return jsonb.fromJson(
                    resp.body().string(),
                    new ArrayList<JiraVersion>(){}.getClass().getGenericSuperclass()
            );
        }
    }

    // ====================== METODI PRIVATI ======================

    private Request.Builder buildAuthenticatedRequest(String user, String pwd, HttpUrl url) {
        Request.Builder rb = new Request.Builder()
                .url(url)
                .header("Accept", "application/json");

        if (user != null && pwd != null) {
            rb.header("Authorization", Credentials.basic(user, pwd));
        }
        return rb;
    }

    private void validateResponse(Response resp) throws IOException {
        if (!resp.isSuccessful()) {
            throw new IOException("JIRA API error: HTTP " + resp.code() + " - " + resp.message());
        }
    }

    private static JiraTicket mapIssue(JiraSearchIssue si) {
        JiraTicket t = new JiraTicket();
        Fields f = si.fields;

        t.setKey(si.key);
        t.setSummary(f.summary);
        t.setDescription(f.description);
        t.setStatus(getSafeName(f.status));
        t.setIssueType(getSafeName(f.issuetype));
        t.setPriority(getSafeName(f.priority));
        t.setReporter(getSafeDisplayName(f.reporter));
        t.setAssignee(getSafeDisplayName(f.assignee));
        t.setResolution(getSafeName(f.resolution));

        // Aggiungi qui altri campi se necessario (es. date, fixVersions, etc.)
        return t;
    }

    // ====================== METODI DI SUPPORTO ======================

    public static String getSafeName(FieldWithName field) {
        return field != null ? field.getName() : null;
    }

    public static String getSafeDisplayName(User user) {
        return user != null ? user.displayName : null;
    }

    // ====================== CLASSI INTERNE PER PARSING JSON ======================

    // Cambia da private a public tutte queste classi:
    public static class JiraSearchResponse {
        public int total;
        public List<JiraSearchIssue> issues;

        public JiraSearchResponse() {} // Aggiungi questo
    }

    public static class JiraSearchIssue {
        public String key;
        public Fields fields;

        public JiraSearchIssue() {}
    }

    public static class Fields {
        public String summary;
        public String description;
        public Status status;
        public IssueType issuetype;
        public Priority priority;
        public User reporter;
        public User assignee;
        public Resolution resolution;

        public Fields() {} // Aggiungi questo
    }

    public interface FieldWithName { String getName(); }
    public static class Status implements FieldWithName { public String name; public String getName() { return name; } }
    public static class IssueType implements FieldWithName { public String name; public String getName() { return name; } }
    public static class Priority implements FieldWithName { public String name; public String getName() { return name; } }
    public static class Resolution implements FieldWithName { public String name; public String getName() { return name; } }
    public static class User { public String displayName; }
}