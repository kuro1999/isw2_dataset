package dataset.creation.fetcher;

import dataset.creation.fetcher.model.JiraTicket;
import dataset.creation.fetcher.model.JiraVersion;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.adapter.JsonbAdapter;
import okhttp3.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scarica le release JIRA e i ticket Bug/Fixed, mappandoli in
 * JiraVersion e JiraTicket. Gestisce date plain e ISO offsets.
 */
public class JiraInjection {

    private static final Logger log = LoggerFactory.getLogger(JiraInjection.class);

    private static final String VERSIONS_API =
            "https://issues.apache.org/jira/rest/api/latest/project/%s";
    private static final String SEARCH_API   =
            "https://issues.apache.org/jira/rest/api/2/search";

    private final OkHttpClient client = new OkHttpClient();
    private final Jsonb jsonb;
    private final String projKey;

    private List<JiraVersion> releases = new ArrayList<>();
    private List<JiraTicket> ticketsWithIssues = new ArrayList<>();

    public JiraInjection(String projKey) {
        this.projKey = Objects.requireNonNull(projKey);
        JsonbConfig cfg = new JsonbConfig().withAdapters(new LocalDateAdapter());
        this.jsonb = JsonbBuilder.create(cfg);
    }

    /** 1) Fetch, filtra e ordina tutte le release */
    public void injectReleases() throws IOException {
        // 1A) Chiamata JIRA
        Request req = new Request.Builder()
                .url(String.format(VERSIONS_API, projKey))
                .header("Accept", "application/json")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException("JIRA versions API HTTP " + resp.code());

            VersionsResponse vr = jsonb.fromJson(resp.body().string(), VersionsResponse.class);
            if (vr.versions != null) {
                releases = vr.versions;
            }
        }

        // 1B) Rimuovo versioni senza nome o data
        List<JiraVersion> filtered = releases.stream()
                .filter(v -> v.getName() != null && v.getReleaseDate() != null)
                .collect(Collectors.toList());
        if (filtered.size() < releases.size()) {
            log.warn("Scartate {} versioni JIRA incomplete (name o releaseDate null) per {}",
                    releases.size() - filtered.size(), projKey);
        }

        // 1C) Rimuovo quelle non in puro semver numerico (es. "0-beta3")
        List<JiraVersion> semverOnly = filtered.stream()
                .filter(v -> v.getName().matches("\\d+(?:\\.\\d+)*"))
                .collect(Collectors.toList());
        if (semverOnly.size() < filtered.size()) {
            log.warn("Scartate {} versioni JIRA non numeric‐semver per {}",
                    filtered.size() - semverOnly.size(), projKey);
        }

        // 1D) Ordino per data (nulls già eliminate)
        semverOnly.sort(Comparator.comparing(
                JiraVersion::getReleaseDate,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        // 1E) Assegno ID sequenziali
        for (int i = 0; i < semverOnly.size(); i++) {
            semverOnly.get(i).setId(i + 1);
        }

        releases = semverOnly;
        log.info("→ caricate {} release JIRA per {}", releases.size(), projKey);
    }

    /** 2) Find first release on or after date */
    private JiraVersion firstOnOrAfter(LocalDate d) {
        return releases.stream()
                .filter(r -> !r.getReleaseDate().isBefore(d))
                .findFirst().orElse(null);
    }

    /** 3) Pull Bug/Fixed tickets, mappandoli in JiraTicket. */
    public void pullIssues() throws IOException {
        ticketsWithIssues.clear();
        int startAt = 0, pageSize = 1000, total;
        String jql = String.format(
                "project=\"%s\" AND issuetype=\"Bug\" AND resolution=\"Fixed\" AND status in (Closed,Resolved)",
                projKey);

        TicketsMapper mapper = new TicketsMapper();

        do {
            HttpUrl url = HttpUrl.parse(SEARCH_API).newBuilder()
                    .addQueryParameter("jql", jql)
                    .addQueryParameter("fields", "*all")
                    .addQueryParameter("startAt", String.valueOf(startAt))
                    .addQueryParameter("maxResults", String.valueOf(pageSize))
                    .build();

            Request req = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful())
                    throw new IOException("JIRA search API HTTP " + resp.code());

                SearchResponse sr = jsonb.fromJson(resp.body().string(), SearchResponse.class);
                total = sr.total;

                for (SearchIssue si : sr.issues) {
                    mapper.mapIssue(si).ifPresent(ticketsWithIssues::add);
                }
            }
            startAt += pageSize;
        } while (startAt < total);

        ticketsWithIssues.sort(Comparator.comparing(JiraTicket::getResolutionDate));
        log.info("→ estratti {} ticket Bug/Fixed per {}", ticketsWithIssues.size(), projKey);
    }

    /** Ritorna la lista delle release JIRA caricate */
    public List<JiraVersion> getReleases() {
        return Collections.unmodifiableList(releases);
    }

    public List<JiraTicket> getTicketsWithIssues() {
        return ticketsWithIssues;
    }

    // --- DTO per JSON-B ---
    public static class VersionsResponse { public List<JiraVersion> versions; }
    public static class SearchResponse  { public int total; public List<SearchIssue> issues; }
    public static class SearchIssue     { public String key; public Fields fields; }

    public static class Fields {
        public String summary;
        public String description;
        public Status status;
        public IssueType issuetype;
        public Priority priority;
        public User reporter;
        public User assignee;
        public Resolution resolution;

        public String created;
        public String updated;
        @JsonbProperty("resolutiondate")
        public String resolutiondate;

        public List<JiraVersion> versions    = new ArrayList<>();
        public List<JiraVersion> fixVersions = new ArrayList<>();

        public List<String> labels        = new ArrayList<>();
        public List<Component> components = new ArrayList<>();
    }

    public static class Status     { public String name; }
    public static class IssueType  { public String name; }
    public static class Priority   { public String name; }
    public static class User       { public String displayName; }
    public static class Resolution { public String name; }
    public static class Component  { public String name; }

    // Adapter per LocalDate ISO+plain
    public static class LocalDateAdapter implements JsonbAdapter<LocalDate,String> {
        private static final DateTimeFormatter ISO_TS =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        @Override public String adaptToJson(LocalDate obj) { return obj.toString(); }
        @Override public LocalDate adaptFromJson(String s) {
            return s.contains("T")
                    ? OffsetDateTime.parse(s, ISO_TS).toLocalDate()
                    : LocalDate.parse(s);
        }
    }

    // Inner mapper non-static
    private class TicketsMapper {
        Optional<JiraTicket> mapIssue(SearchIssue si) {
            if (si.fields == null || si.fields.created == null || si.fields.resolutiondate == null)
                return Optional.empty();

            LocalDate created  = LocalDate.parse(si.fields.created.substring(0,10));
            LocalDate resolved = LocalDate.parse(si.fields.resolutiondate.substring(0,10));

            JiraVersion opening = firstOnOrAfter(created);
            JiraVersion fixed   = firstOnOrAfter(resolved);
            if (opening == null || fixed == null) return Optional.empty();

            List<JiraVersion> affected = new ArrayList<>();
            for (JiraVersion v : si.fields.versions) {
                releases.stream()
                        .filter(r -> r.getName().equals(v.getName()))
                        .findFirst()
                        .ifPresent(affected::add);
            }
            affected.sort(Comparator.comparing(JiraVersion::getReleaseDate));

            // filtro coerenza
            boolean ok = !affected.isEmpty()
                    && !affected.get(0).getReleaseDate().isBefore(opening.getReleaseDate())
                    && !opening.getReleaseDate().isAfter(fixed.getReleaseDate());
            if (!ok) return Optional.empty();

            JiraTicket t = new JiraTicket();
            t.setKey(si.key);
            t.setSummary(si.fields.summary);
            t.setDescription(si.fields.description);
            t.setStatus(si.fields.status != null ? si.fields.status.name : null);
            t.setIssueType(si.fields.issuetype != null ? si.fields.issuetype.name : null);
            t.setPriority(si.fields.priority != null ? si.fields.priority.name : null);
            t.setReporter(si.fields.reporter != null ? si.fields.reporter.displayName : null);
            t.setAssignee(si.fields.assignee != null ? si.fields.assignee.displayName : null);
            t.setResolution(si.fields.resolution != null ? si.fields.resolution.name : null);

            t.setCreationDate(created);
            t.setResolutionDate(resolved);
            if (si.fields.updated != null)
                t.setUpdatedDate(LocalDate.parse(si.fields.updated.substring(0,10)));

            if (!si.fields.fixVersions.isEmpty())
                t.setFixedVersion(si.fields.fixVersions.get(0));
            if (!si.fields.versions.isEmpty())
                t.setOpeningVersion(si.fields.versions.get(0));
            t.setAffectedVersions(affected);

            t.setLabels(si.fields.labels);
            t.setComponents(si.fields.components.stream().map(c -> c.name).collect(Collectors.toList()));

            return Optional.of(t);
        }
    }
}
