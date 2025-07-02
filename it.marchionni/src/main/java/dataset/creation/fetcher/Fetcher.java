package dataset.creation.fetcher;

import dataset.creation.fetcher.fields.FieldWithName;
import dataset.creation.fetcher.fields.Fields;
import dataset.creation.fetcher.fields.User;
import dataset.creation.fetcher.jira.JiraSearchIssue;
import dataset.creation.fetcher.jira.JiraSearchResponse;
import dataset.creation.fetcher.jira.JiraTicket;
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
        int startAt = 0;
        int pageSize = 500;
        int total;
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
                total = sr.getTotal();
                sr.getIssues().forEach(issue -> tickets.add(mapIssue(issue)));
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
        Fields f = si.getFields();

        t.setKey(si.getKey());
        t.setSummary(f.getSummary());
        t.setDescription(f.getDescription());
        t.setStatus(getSafeName(f.getStatus()));
        t.setIssueType(getSafeName(f.getIssuetype()));
        t.setPriority(getSafeName(f.getPriority()));
        t.setReporter(getSafeDisplayName(f.getReporter()));
        t.setAssignee(getSafeDisplayName(f.getAssignee()));
        t.setResolution(getSafeName(f.getResolution()));

        // Aggiungi qui altri campi se necessario (es. date, fixVersions, etc.)
        return t;
    }

    // ====================== METODI DI SUPPORTO ======================

    public static String getSafeName(FieldWithName field) {
        return field != null ? field.getName() : null;
    }

    public static String getSafeDisplayName(User user) {
        return user != null ? user.getDisplayName() : null;
    }
}