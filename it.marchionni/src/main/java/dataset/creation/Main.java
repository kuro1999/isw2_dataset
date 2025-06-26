package dataset.creation;

import dataset.creation.fetcher.BookkeeperFetcher;
import dataset.creation.fetcher.JiraInjection;
import dataset.creation.fetcher.GitInjection;
import dataset.creation.fetcher.model.JiraTicket;
import dataset.creation.fetcher.model.JiraVersion;
import dataset.creation.features.FeatureExtractor;
import dataset.creation.features.BuggyMethodExtractor;
import dataset.creation.features.BuggyMethodExtractor.BuggyInfo;
import dataset.creation.features.CsvGenerator;
import dataset.creation.utils.CsvDeduplicator;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Path   BK_REPO = Path.of("/home/edo/isw2/bookkeeper_isw2");
    private static final OkHttpClient http = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static final String OWNER = "apache";
    private static final String REPO  = "bookkeeper";

    public static void main(String[] args) {
        try {
            log.info("Avvio processo dataset BOOKKEEPER");
            BookkeeperFetcher fetcher = new BookkeeperFetcher();
            List<JiraTicket> tickets  = loadOrDownloadTickets(fetcher);

            /* 1️⃣  Release da JIRA */
            JiraInjection jiraInj = new JiraInjection("BOOKKEEPER");
            jiraInj.injectReleases();
            List<JiraVersion> jiraReleases = jiraInj.getReleases();
            log.info("Release JIRA: {}", jiraReleases.size());

            Path jiraFile = Paths.get("jira_releases.txt");
            List<String> jiraNames = jiraReleases.stream()
                    .map(JiraVersion::getName)
                    .collect(Collectors.toList());
            Files.write(jiraFile, jiraNames, StandardCharsets.UTF_8);
            log.info("File release JIRA: {}", jiraFile.toAbsolutePath());

            /* 2️⃣  Tag da GitHub */
            List<String> gitTags = fetchGitHubTags(OWNER, REPO);
            log.info("Tag GitHub: {}", gitTags.size());

            Path gitFile = Paths.get("git_tags.txt");
            Files.write(gitFile, gitTags, StandardCharsets.UTF_8);
            log.info("File tag Git: {}", gitFile.toAbsolutePath());

            /* 3️⃣  Intersezione */
            Set<String> jiraNormalized = jiraNames.stream()
                    .map(Main::normalize)
                    .collect(Collectors.toSet());
            List<String> releases = gitTags.stream()
                    .filter(t -> jiraNormalized.contains(normalize(t)))
                    .sorted(Main::compareSemver)
                    .collect(Collectors.toList());
            if (releases.isEmpty()) {
                log.warn("Nessun tag comune: userò HEAD");
                releases = List.of("HEAD");
            }
            log.info("Release da elaborare: {}", releases);

            Path commonFile = Paths.get("common_releases.txt");
            Files.write(commonFile, releases, StandardCharsets.UTF_8);
            log.info("File release comuni: {}", commonFile.toAbsolutePath());

            /* 4️⃣  Commit dal repo */
            GitInjection gitInj = new GitInjection(
                    repoRemoteUrl(), BK_REPO.toFile(), new ArrayList<>()
            );
            gitInj.injectCommits();

            /* 5️⃣  Estrazione feature e CSV */
            FeatureExtractor fx = new FeatureExtractor();
            BuggyInfo bugInfo  = BuggyMethodExtractor.computeOrLoad(
                    BK_REPO.toFile(), tickets
            );

            String csvName = "dataset_bookkeeper.csv";
            boolean first = true;
            for (String tag : releases) {
                log.info("Elaboro release {}", tag);
                Map<File, Map<String, FeatureExtractor.MethodFeatures>> feats;
                if ("HEAD".equals(tag)) {
                    feats = walkAndExtract(BK_REPO.toFile(), fx);
                } else {
                    Path tmp = downloadAndUnzip(OWNER, REPO, tag);
                    Path proj = findSingleSubdir(tmp);
                    feats = walkAndExtract(proj.toFile(), fx);
                    deleteDirectoryRecursively(tmp);
                }
                new CsvGenerator(tag, !first)
                        .generateCsv(feats, bugInfo, csvName);
                first = false;
            }
            log.info("CSV grezzo salvato in {}", Paths.get(csvName).toAbsolutePath());

            /* 6️⃣  Deduplicazione e filtro su dataset CSV */
            Path rawCsv       = Paths.get(csvName);
            Path dedupCsv     = Paths.get("dataset_bookkeeper_dedup.csv");
            CsvDeduplicator.deduplicate(rawCsv, dedupCsv);
            log.info("CSV deduplicato salvato in {}", dedupCsv.toAbsolutePath());

            Path filteredCsv  = Paths.get("dataset_bookkeeper_filtered.csv");
            CsvDeduplicator.dedupAndFilterUpTo(dedupCsv, filteredCsv, "4.2.1");
            log.info("CSV filtrato fino a 4.2.1 salvato in {}", filteredCsv.toAbsolutePath());

        } catch (Exception e) {
            log.error("Errore esecuzione", e);
        }
    }

    /* Utility private (unchanged) */
    private static String repoRemoteUrl() throws IOException {
        Repository r = new FileRepositoryBuilder()
                .setGitDir(BK_REPO.resolve(".git").toFile())
                .readEnvironment().findGitDir().build();
        return r.getConfig().getString("remote", "origin", "url");
    }

    @SuppressWarnings("unchecked")
    private static List<JiraTicket> loadOrDownloadTickets(BookkeeperFetcher f) throws Exception {
        Path json = Paths.get("bookkeeper_jira_tickets.json");
        Jsonb jb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));
        if (Files.exists(json)) {
            try (Reader r = Files.newBufferedReader(json)) {
                Type t = new ArrayList<JiraTicket>() {}.getClass().getGenericSuperclass();
                return (List<JiraTicket>) jb.fromJson(r, t);
            }
        }
        List<JiraTicket> ts = f.fetchAllJiraTickets(
                System.getenv("JIRA_USER"), System.getenv("JIRA_PASS")
        );
        f.writeTicketsToJsonFile(ts, json.toString());
        return ts;
    }

    private static List<String> fetchGitHubTags(String owner, String repo) throws IOException {
        HttpUrl url = HttpUrl.parse(
                "https://api.github.com/repos/" + owner + "/" + repo + "/tags"
        ).newBuilder().addQueryParameter("per_page", "100").build();
        Request req = new Request.Builder().url(url).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("GitHub tags failed: " + resp);
            JsonArray arr = gson.fromJson(resp.body().charStream(), JsonArray.class);
            List<String> tags = new ArrayList<>();
            for (JsonElement el : arr) {
                tags.add(el.getAsJsonObject().get("name").getAsString());
            }
            return tags;
        }
    }

    private static Path downloadAndUnzip(String owner, String repo, String tag) throws IOException {
        HttpUrl url = HttpUrl.parse(
                "https://api.github.com/repos/" + owner + "/" + repo + "/zipball/" + tag
        );
        Request req = new Request.Builder().url(url).build();
        Path tmp = Files.createTempDirectory(repo + "-" + tag + "-");
        try (Response resp = http.newCall(req).execute();
             InputStream in = resp.body().byteStream();
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                Path out = tmp.resolve(e.getName());
                if (e.isDirectory()) Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out);
                }
                zip.closeEntry();
            }
        }
        return tmp;
    }

    private static Path findSingleSubdir(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> subs = s.filter(Files::isDirectory).collect(Collectors.toList());
            return subs.size() == 1 ? subs.get(0) : dir;
        }
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private static Map<File, Map<String, FeatureExtractor.MethodFeatures>> walkAndExtract(
            File dir, FeatureExtractor fx) throws IOException {
        Map<File, Map<String, FeatureExtractor.MethodFeatures>> m = new HashMap<>();
        try (Stream<Path> ps = Files.walk(dir.toPath())) {
            ps.filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/java/"))
                    .map(Path::toFile)
                    .forEach(f -> {
                        try { m.put(f, fx.extractFromFile(f)); }
                        catch (Exception ignored) {}
                    });
        }
        return m;
    }

    private static String normalize(String tag) {
        return tag.replaceFirst("^(?:v|release-)", "");
    }

    public static int compareSemver(String a, String b) {
        String[] pa = normalize(a).split("\\.");
        String[] pb = normalize(b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int ai = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int bi = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }
}
