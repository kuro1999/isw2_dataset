package dataset.creation.fetcher;

import dataset.creation.fetcher.model.*;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.adapter.JsonbAdapter;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unico “fetcher” per:
 *   • versioni JIRA
 *   • ticket Bug/Fixed (con tutti i campi)
 *   • tag GitHub corrispondenti alle versioni selezionate
 */
public class BookkeeperFetcher {

    /* ------------------------- costanti API ------------------------- */
    private static final String JIRA_PROJECT_API =
            "https://issues.apache.org/jira/rest/api/latest/project/%s";
    private static final String JIRA_SEARCH_API  =
            "https://issues.apache.org/jira/rest/api/2/search";
    private static final String JIRA_JQL_BUG_FIXED =
            "project = BOOKKEEPER AND issuetype = \"Bug\" " +
                    "AND resolution = \"Fixed\" AND status IN (\"Closed\",\"Resolved\")";

    private static final String GITHUB_TAGS_API   =
            "https://api.github.com/repos/apache/bookkeeper/tags";
    private static final String GITHUB_COMMIT_API =
            "https://api.github.com/repos/apache/bookkeeper/git/commits/%s";

    /* ------------------------- infra ------------------------- */
    private final OkHttpClient client = new OkHttpClient();
    private final Jsonb jsonb;

    public BookkeeperFetcher() {
        jsonb = JsonbBuilder.create(
                new JsonbConfig().withAdapters(new LocalDateAdapter(), new InstantAdapter())
                        .withFormatting(true));
    }

    /* =========================================================
       JIRA – versioni
       ========================================================= */
    public List<JiraVersion> fetchJiraVersions(String user, String pwd) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(String.format(JIRA_PROJECT_API, "BOOKKEEPER"))
                .header("Accept", "application/json");
        if (user != null && pwd != null)
            rb.header("Authorization", Credentials.basic(user, pwd));

        try (Response r = client.newCall(rb.build()).execute()) {
            if (!r.isSuccessful())
                throw new IOException("HTTP " + r.code());

            ProjectResponse pr = jsonb.fromJson(r.body().string(), ProjectResponse.class);
            List<JiraVersion> list = pr.versions.stream()
                    .filter(v -> v.getReleaseDate() != null)
                    .sorted(Comparator.comparing(JiraVersion::getReleaseDate))
                    .collect(Collectors.toList());

            /* id progressivi */
            for (int i = 0; i < list.size(); i++) list.get(i).setId(i + 1);
            return list;
        }
    }

    public List<JiraVersion> selectOldest34Percent(List<JiraVersion> versions) {
        int keep = Math.max(1, (int)Math.ceil(versions.size() * 0.34));
        return new ArrayList<>(versions.subList(0, keep));
    }

    /* =========================================================
       JIRA – ticket Bug/Fixed completi
       ========================================================= */
    public List<JiraTicket> fetchFixedJiraTickets(String user, String pwd) throws IOException {

        List<JiraTicket> tickets = new ArrayList<>();
        int startAt = 0, pageSize = 500, total;

        do {
            HttpUrl url = HttpUrl.parse(JIRA_SEARCH_API).newBuilder()
                    .addQueryParameter("jql", JIRA_JQL_BUG_FIXED)
                    .addQueryParameter("fields", "*all")
                    .addQueryParameter("startAt", String.valueOf(startAt))
                    .addQueryParameter("maxResults", String.valueOf(pageSize))
                    .build();

            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json");
            if (user != null && pwd != null)
                rb.header("Authorization", Credentials.basic(user, pwd));

            try (Response resp = client.newCall(rb.build()).execute()) {
                if (!resp.isSuccessful())
                    throw new IOException("JIRA search API error: " + resp.code());

                JiraSearchResponse sr = jsonb.fromJson(resp.body().string(), JiraSearchResponse.class);
                total = sr.total;

                for (JiraSearchIssue si : sr.issues) {
                    Fields f = si.fields;
                    JiraTicket t = new JiraTicket();

                    t.setKey(si.key);
                    t.setSummary(f.summary);
                    t.setDescription(f.description);
                    t.setStatus(f.status != null ? f.status.name : null);
                    t.setIssueType(f.issuetype != null ? f.issuetype.name : null);
                    t.setPriority(f.priority != null ? f.priority.name : null);
                    t.setReporter(f.reporter != null ? f.reporter.displayName : null);
                    t.setAssignee(f.assignee != null ? f.assignee.displayName : null);
                    t.setResolution(f.resolution != null ? f.resolution.name : null);

                    if (f.created != null)         t.setCreationDate(LocalDate.parse(f.created.substring(0,10)));
                    if (f.resolutiondate != null)  t.setResolutionDate(LocalDate.parse(f.resolutiondate.substring(0,10)));
                    if (f.updated != null)         t.setUpdatedDate(LocalDate.parse(f.updated.substring(0,10)));

                    if (!f.fixVersions.isEmpty())  t.setFixedVersion(f.fixVersions.get(0));
                    if (!f.versions.isEmpty())     t.setOpeningVersion(f.versions.get(0));
                    t.setAffectedVersions(f.versions);

                    t.setLabels(f.labels);
                    t.setComponents(f.components.stream().map(c -> c.name).collect(Collectors.toList()));

                    tickets.add(t);
                }
            }
            startAt += pageSize;
        } while (startAt < total);

        return tickets;
    }

    /* =========================================================
       GitHub – tag corrispondenti alle versioni passate
       ========================================================= */
    public List<Release> fetchReleasesForVersions(List<JiraVersion> versions) throws IOException {

        Set<String> wanted = versions.stream()
                .flatMap(v -> Stream.of(
                        v.getName(),
                        "v" + v.getName(),
                        "release-" + v.getName()))
                .collect(Collectors.toSet());

        Map<String, Instant> tagToDate = new HashMap<>();
        String token = System.getenv("GITHUB_TOKEN");

        int page = 1;
        while (true) {
            HttpUrl url = HttpUrl.parse(GITHUB_TAGS_API).newBuilder()
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("page",     String.valueOf(page))
                    .build();

            Request req = new Request.Builder()
                    .url(url)
                    .header("Accept",        "application/vnd.github+json")
                    .header("Authorization", "token " + token)
                    .build();

            Tag[] batch = jsonb.fromJson(client.newCall(req).execute().body().string(), Tag[].class);
            if (batch.length == 0) break;

            for (Tag t : batch) {
                if (!wanted.contains(t.name)) continue;

                Request cReq = new Request.Builder()
                        .url(String.format(GITHUB_COMMIT_API, t.commit.sha))
                        .header("Accept",        "application/vnd.github+json")
                        .header("Authorization", "token " + token)
                        .build();

                CommitInfo ci = jsonb.fromJson(client.newCall(cReq).execute().body().string(), CommitInfo.class);
                tagToDate.put(t.name, Instant.parse(ci.committer.date));
            }
            page++;
        }

        return tagToDate.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(e -> new Release(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /* =========================================================
       DTO interni per JSON-B
       ========================================================= */
    /* project versions */
    public static class ProjectResponse { public List<JiraVersion> versions; }

    /* tickets */
    public static class JiraSearchResponse { public int total; public List<JiraSearchIssue> issues; }
    public static class JiraSearchIssue    { public String key; public Fields fields; }

    public static class Fields {
        public String summary;
        public String description;
        public Status     status;
        public IssueType  issuetype;
        public Priority   priority;
        public User       reporter;
        public User       assignee;
        public Resolution resolution;

        public String created;
        public String updated;
        @JsonbProperty("resolutiondate")
        public String resolutiondate;

        public List<JiraVersion> versions    = new ArrayList<>();
        public List<JiraVersion> fixVersions = new ArrayList<>();

        public List<String> labels           = new ArrayList<>();
        public List<Component> components    = new ArrayList<>();
    }

    public static class Status     { public String name; }
    public static class IssueType  { public String name; }
    public static class Priority   { public String name; }
    public static class User       { public String displayName; }
    public static class Resolution { public String name; }
    public static class Component  { public String name; }

    /* GitHub tag DTO */
    public static class Tag { public String name; public CommitRef commit; }
    public static class CommitRef { public String sha; }
    public static class CommitInfo { public GitPerson committer; }
    public static class GitPerson { public String date; }

    /* =========================================================
       JSON-B adapters
       ========================================================= */
    public static class LocalDateAdapter implements JsonbAdapter<LocalDate,String> {
        @Override public String adaptToJson(LocalDate obj) { return obj.toString(); }
        @Override public LocalDate adaptFromJson(String s) { return LocalDate.parse(s.substring(0,10)); }
    }
    public static class InstantAdapter implements JsonbAdapter<Instant,String> {
        @Override public String adaptToJson(Instant i) { return i.toString(); }
        @Override public Instant adaptFromJson(String s){ return Instant.parse(s); }
    }
}
