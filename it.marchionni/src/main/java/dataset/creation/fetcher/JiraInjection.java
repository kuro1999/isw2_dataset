package dataset.creation.fetcher;

import dataset.creation.fetcher.jira.JiraVersion;
import jakarta.json.*;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import okhttp3.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class JiraInjection {

    private static final Logger log = LoggerFactory.getLogger(JiraInjection.class);

    private static final String VERSIONS_API =
            "https://issues.apache.org/jira/rest/api/latest/project/%s";

    private final OkHttpClient client = new OkHttpClient();
    private final Jsonb jsonb;
    private final String projKey;

    private List<JiraVersion> releases = new ArrayList<>();

    public JiraInjection(String projKey) {
        this.projKey = Objects.requireNonNull(projKey);
        JsonbConfig cfg = new JsonbConfig()
                // se hai già un adapter per LocalDate in JiraVersion
                .withAdapters(new LocalDateAdapter());
        this.jsonb = JsonbBuilder.create(cfg);
    }

    /** 1) Fetch, filtra e ordina tutte le release (JSON-P + JSON-B solo sull’array) */
    public void injectReleases() throws IOException {
        Request req = new Request.Builder()
                .url(String.format(VERSIONS_API, projKey))
                .header("Accept", "application/json")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException("JIRA versions API HTTP " + resp.code());

            String body = resp.body().string();

            // JSON-P: prendo l'array "versions" chiudendo correttamente reader/stream
            JsonArray arr;
            try (StringReader sr = new StringReader(body);
                 JsonReader jr = Json.createReader(sr)) {
                JsonObject root = jr.readObject();
                arr = root.getJsonArray("versions");
            }

            // JSON-B: deserializzo direttamente in array e poi in List<JiraVersion>
            JiraVersion[] versionsArray = jsonb.fromJson(arr.toString(), JiraVersion[].class);
            List<JiraVersion> all        = Arrays.asList(versionsArray);

            // filtri e ordinamento identico a prima
            List<JiraVersion> filtered = all.stream()
                    .filter(v -> v.getName() != null && v.getReleaseDate() != null)
                    .filter(v -> v.getName().matches("\\d+(?>\\.\\d+)*"))  // atomic group per evitare backtracking
                    .sorted(Comparator.comparing(JiraVersion::getReleaseDate))
                    .collect(Collectors.toList());

            for (int i = 0; i < filtered.size(); i++) {
                filtered.get(i).setId(i + 1);
            }
            releases = filtered;
            log.info("→ caricate {} release JIRA per {}", releases.size(), projKey);
        }
    }


    public List<JiraVersion> getReleases() {
        return Collections.unmodifiableList(releases);
    }

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
}
