package dataset.creation;

import dataset.creation.fetcher.BookkeeperFetcher;
import dataset.creation.fetcher.model.JiraTicket;
import dataset.creation.features.FeatureExtractor;
import dataset.creation.features.BuggyMethodExtractor;
import dataset.creation.features.BuggyMethodExtractor.BuggyInfo;
import dataset.creation.features.CsvGenerator;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /** clona il repo una volta sola, poi ri-usa la directory */
    private static final Path BK_REPO = Path.of("/home/edo/isw2/bookkeeper_isw2");

    public static void main(String[] args) throws Exception {
        log.info("Avvio del processo di esportazione e creazione del dataset");

        /* ---------- 1. Ticket JIRA ------------------------------------------------ */
        BookkeeperFetcher fetcher = new BookkeeperFetcher();
        List<JiraTicket> tickets  = loadOrDownloadTickets(fetcher);

        /* ---------- 2. Repo Git locale -------------------------------------------- */
        if (!Files.isDirectory(BK_REPO.resolve(".git"))) {
            log.error("Repository non trovato in {}", BK_REPO);
            return;
        }
        var repo = new FileRepositoryBuilder()
                .setGitDir(BK_REPO.resolve(".git").toFile())
                .readEnvironment().findGitDir().build();
        Git git = new Git(repo);

        /* ---------- 3. fetch --tags (completa) ------------------------------------ */
        log.info("Fetch remota dei tag…");
        git.fetch()
                .setRemote("origin")
                .setTagOpt(TagOpt.FETCH_TAGS)
                .setRefSpecs(new RefSpec("+refs/tags/*:refs/tags/*"))
                .call();

        /* ---------- 4. tag locali + tag remoti via ls-remote ---------------------- */
        Set<String> gitTagsRaw = new HashSet<>();

        // 4a) quelli già presenti in .git
        gitTagsRaw.addAll(
                git.tagList().call().stream()
                        .map(Ref::getName)               // refs/tags/v4.0.0
                        .map(s -> s.replace("refs/tags/", "")).collect(Collectors.toList()));
        ;

        // 4b) se non bastano interrogo direttamente GitHub
        String remoteUrl = repo.getConfig().getString("remote", "origin", "url");
        gitTagsRaw.addAll(
                Git.lsRemoteRepository()
                        .setRemote(remoteUrl)
                        .setTags(true)
                        .call()
                        .stream()
                        .map(Ref::getName)
                        .filter(n -> !n.endsWith("^{}"))  // ignora i peel tag degli annotated
                        .map(n -> n.replace("refs/tags/", "")).collect(Collectors.toList()));
        ;

        log.info("Tag Git (locali + remoti) individuati: {}", gitTagsRaw.size());

        /* ---------- 5. versioni JIRA --------------------------------------------- */
        List<String> jiraVers = fetcher.fetchProjectVersions(
                System.getenv("JIRA_USER"), System.getenv("JIRA_PASS"));
        log.info("Versioni JIRA: {}", jiraVers.size());

        /* ---------- 6. matching Git∩Jira con normalizzazione ---------------------- */
        Set<String> jiraNorm = jiraVers.stream()
                .map(Main::normalize)
                .collect(Collectors.toSet());

        List<String> releases = gitTagsRaw.stream()
                .filter(t -> jiraNorm.contains(normalize(t)))
                .sorted(Main::compareSemver)
                .collect(Collectors.toList());

        if (releases.isEmpty()) {
            log.warn("Nessun tag Git corrisponde alle versioni JIRA → stop.");
            return;
        }
        log.info("Tag validi (ordinati): {}", releases);

        /* ---------- 7. cache buggy / else / churn --------------------------------- */
        BuggyInfo bugInfo = BuggyMethodExtractor.computeOrLoad(BK_REPO.toFile(), tickets);
        log.info("Metodi buggy (cache): {}", bugInfo.buggyMethods.size());

        /* ---------- 8. estrazione + CSV per ogni release -------------------------- */
        FeatureExtractor extractor = new FeatureExtractor();
        boolean first = true;
        String  csv   = "dataset_bookkeeper.csv";

        for (String tag : releases) {
            log.info("▶ Checkout {}", tag);
            git.checkout().setName(tag).call();

            Map<File, Map<String, FeatureExtractor.MethodFeatures>> feats = new HashMap<>();
            try (Stream<Path> paths = Files.walk(BK_REPO)) {
                paths.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> p.toString().contains("/src/main/java/"))
                        .map(Path::toFile)
                        .forEach(f -> {
                            try { feats.put(f, extractor.extractFromFile(f)); }
                            catch (Exception e) { log.warn("Parse {}: {}", f, e.getMessage()); }
                        });
            }
            log.info("Feature estratte: {}", feats.size());

            new CsvGenerator(tag, !first).generateCsv(feats, bugInfo, csv);
            first = false;
        }
        log.info("✓ Dataset completo salvato in {}", csv);
    }

    /* ------------------------------------------------------------------------- */
    private static List<JiraTicket> loadOrDownloadTickets(BookkeeperFetcher f) throws Exception {
        Path json = Path.of("bookkeeper_jira_tickets.json");
        Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));

        if (Files.exists(json)) {
            try (Reader r = Files.newBufferedReader(json)) {
                Type t = new ArrayList<JiraTicket>(){}.getClass().getGenericSuperclass();
                var list = jsonb.fromJson(r, t);
                log.info("Ticket letti da cache locale");
                return (List<JiraTicket>) list;
            }
        }
        log.info("Ticket non in cache → download da JIRA");
        String u = System.getenv("JIRA_USER"), p = System.getenv("JIRA_PASS");
        var tickets = f.fetchAllJiraTickets(u, p);
        f.writeTicketsToJsonFile(tickets, json.toString());
        return tickets;
    }

    /* normalizza: toglie prefissi ‘v’, ‘release-’ e tutto ciò che non è [0-9.] */
    private static String normalize(String tag) {
        return tag.replaceFirst("^(?:v|release-)", "");
    }

    /* semver element-wise */
    private static int compareSemver(String a, String b) {
        String[] pa = normalize(a).split("\\.");
        String[] pb = normalize(b).split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int ai = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int bi = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }
}
