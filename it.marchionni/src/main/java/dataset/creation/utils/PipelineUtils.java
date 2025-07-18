package dataset.creation.utils;

import dataset.creation.features.FeatureExtractor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import dataset.creation.features.MethodFeatures;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Raccolta di utility statiche ri-usabili dalla pipeline
 * per QUALSIASI progetto (BookKeeper, OpenJPA, …).
 */
public final class PipelineUtils {

    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final Gson         GSON = new Gson();

    private PipelineUtils() { /* utility class */ }

    /* =========================================================
       FILTRI GLOB PER walkAndExtract
       ========================================================= */

    public static final PathMatcher TEST_DIR_MATCHER       =
            FileSystems.getDefault().getPathMatcher("glob:**/src/test/java/**");
    public static final PathMatcher TEST_CLASS_MATCHER     =
            FileSystems.getDefault().getPathMatcher("glob:**/*{Test,IT}.java");
    public static final PathMatcher MAIN_TESTS_DIR_MATCHER =
            FileSystems.getDefault().getPathMatcher("glob:**/src/main/java/tests/**");
    public static final PathMatcher IGNORE_MATCHER         =
            FileSystems.getDefault().getPathMatcher("glob:**/{target,build,generated-sources}/**");
    public static final PathMatcher DEMO_DIR_MATCHER       =
            FileSystems.getDefault().getPathMatcher("glob:**/{demo,sample,example}/**");
    public static final PathMatcher DEMO_CLASS_MATCHER     =
            FileSystems.getDefault().getPathMatcher("glob:**/*{Demo,Sample,Example}.java");
    public static final PathMatcher MOCK_DIR_MATCHER       =
            FileSystems.getDefault().getPathMatcher("glob:**/{mock,stubs,test-data}/**");
    public static final PathMatcher MOCK_CLASS_MATCHER     =
            FileSystems.getDefault().getPathMatcher("glob:**/*Mock.java");
    public static final PathMatcher STUB_CLASS_MATCHER     =
            FileSystems.getDefault().getPathMatcher("glob:**/*Stub.java");
    public static final PathMatcher TESTDATA_CLASS_MATCHER =
            FileSystems.getDefault().getPathMatcher("glob:**/*TestData.java");
    public static final PathMatcher BENCH_DIR_MATCHER      =
            FileSystems.getDefault().getPathMatcher("glob:**/benchmark/**");
    public static final PathMatcher BENCH_CLASS_MATCHER    =
            FileSystems.getDefault().getPathMatcher("glob:**/*Benchmark.java");

    /** Array pubblico dei filtri da usare in walkAndExtract */
    protected static final PathMatcher[] DEFAULT_FILTERS = {
            TEST_DIR_MATCHER, TEST_CLASS_MATCHER, MAIN_TESTS_DIR_MATCHER,
            IGNORE_MATCHER,
            DEMO_DIR_MATCHER, DEMO_CLASS_MATCHER,
            MOCK_DIR_MATCHER, MOCK_CLASS_MATCHER, STUB_CLASS_MATCHER, TESTDATA_CLASS_MATCHER,
            BENCH_DIR_MATCHER, BENCH_CLASS_MATCHER
    };

    /* =========================================================
       REPO & GITHUB
       ========================================================= */

    /** Restituisce l’URL remoto (origin) di una clone Git. */
    public static String repoRemoteUrl(Path projectRoot) throws IOException {
        Repository r = new FileRepositoryBuilder()
                .setGitDir(projectRoot.resolve(".git").toFile())
                .readEnvironment().findGitDir().build();
        return r.getConfig().getString("remote", "origin", "url");
    }

    /** Ottiene tutti i tag GitHub (paginati 100/call). */
    public static List<String> fetchGitHubTags(String owner, String repo) throws IOException {
        HttpUrl url = HttpUrl.parse(
                "https://api.github.com/repos/" + owner + "/" + repo + "/tags"
        ).newBuilder().addQueryParameter("per_page", "100").build();

        Request req = new Request.Builder().url(url).build();
        try (Response resp = HTTP.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException("GitHub tags failed: " + resp);

            JsonArray arr = GSON.fromJson(resp.body().charStream(), JsonArray.class);
            List<String> tags = new ArrayList<>();
            for (JsonElement el : arr) {
                tags.add(el.getAsJsonObject().get("name").getAsString());
            }
            return tags;
        }
    }

    /* =========================================================
       ZIP di una release GitHub
       ========================================================= */

    /** Scarica ZIP di una release/tag e lo espande in una dir temporanea. */
    public static Path downloadAndUnzip(String owner, String repo, String tag) throws IOException {
        HttpUrl url = HttpUrl.parse(
                "https://api.github.com/repos/" + owner + "/" + repo + "/zipball/" + tag
        );

        Request req = new Request.Builder().url(url).build();
        Path tmp    = Files.createTempDirectory(repo + "-" + tag + "-");

        try (Response resp = HTTP.newCall(req).execute();
             InputStream in = resp.body().byteStream();
             ZipInputStream zip = new ZipInputStream(in)) {

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                // Normalizza il percorso e previene Zip Slip
                Path normalized = tmp.resolve(entry.getName()).normalize();
                if (!normalized.startsWith(tmp)) {
                    throw new IOException("Invalid ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(normalized);
                } else {
                    Files.createDirectories(normalized.getParent());
                    try (OutputStream out = Files.newOutputStream(normalized)) {
                        zip.transferTo(out);
                    }
                }
                zip.closeEntry();
            }
        }
        return tmp;
    }

    /** Se lo ZIP contiene una sola sottodirectory, la restituisce; altrimenti torna dir. */
    public static Path findSingleSubdir(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> subs = s.filter(Files::isDirectory).collect(Collectors.toList());
            return subs.size() == 1 ? subs.get(0) : dir;
        }
    }

    /** Delete-rec ricorsivo (anche su grosse dir). */
    public static void deleteDirectoryRecursively(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    /* =========================================================
       WALK + FEATURE EXTRACTION CON FILTRI
       ========================================================= */

    /**
     * Esegue un walk ricorsivo e applica i matcher di esclusione.
     * @param dir      root del progetto / modulo da analizzare
     * @param fx       extractor già istanziato
     */
    public static Map<File, Map<String, MethodFeatures>>
    walkAndExtract(File dir,
                   FeatureExtractor fx) throws IOException {

        Collection<PathMatcher> ex = Arrays.asList(DEFAULT_FILTERS);
        Map<File, Map<String, MethodFeatures>> out = new HashMap<>();

        try (Stream<Path> ps = Files.walk(dir.toPath())) {
            ps.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> ex.stream().noneMatch(m -> m.matches(p)))
                    .map(Path::toFile)
                    .forEach(f -> {
                        try { out.put(f, fx.extractFromFile(f)); }
                        catch (Exception ignored) { /* skip file on error */ }
                    });
        }
        return out;
    }

    /* =========================================================
       VARI UTILITY
       ========================================================= */

    /** Normalizza un tag rimuovendo “v” o “release-” in testa. */
    public static String normalize(String tag) {
        return tag.replaceFirst("^(?:v|release-)", "");
    }

    /** Confronto semver “alla buona” (major.minor.patch …). */
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
