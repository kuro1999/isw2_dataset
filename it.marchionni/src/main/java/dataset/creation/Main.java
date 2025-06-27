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
import dataset.creation.utils.FinalCsvReducer;
import dataset.creation.utils.PipelineUtils;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static dataset.creation.utils.PipelineUtils.DEFAULT_FILTERS;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        // ① Definisci qui i progetti da processare:
        List<ProjectConfig> projects = List.of(
                new ProjectConfig("apache", "openjpa", "OPENJPA",
                        Path.of("/home/edo/isw2/openjpa_isw2"),
                        null, Map.of()),
                new ProjectConfig("apache", "bookkeeper", "BOOKKEEPER",
                        Path.of("/home/edo/isw2/bookkeeper_isw2"),
                        "4.2.1", Map.of())
        );

        for (ProjectConfig cfg : projects) {
            try {
                runPipelineFor(cfg);
            } catch (Exception e) {
                LOG.error("🔴 Errore nella pipeline per {}:", cfg.repo(), e);
            }
        }
    }

    private static void runPipelineFor(ProjectConfig cfg) throws Exception {
        LOG.info("▶ Avvio pipeline dataset per {}", cfg.repo());

        // 1) Prepara cartella di cache per questo progetto
        Path cacheDir = Paths.get("cache", cfg.repo().toLowerCase());
        Files.createDirectories(cacheDir);

        // 2) JIRA tickets + cache
        BookkeeperFetcher fetcher = new BookkeeperFetcher();
        List<JiraTicket> tickets = loadOrDownloadTickets(fetcher, cfg, cacheDir);

        // 3) Fetch e dump delle versioni JIRA
        JiraInjection jira = new JiraInjection(cfg.jiraProject());
        jira.injectReleases();
        List<JiraVersion> rawJiraRel = jira.getReleases().stream()
                .filter(v -> v.getName() != null)
                .collect(Collectors.toList());
        dumpJson(cacheDir, cfg.repo().toLowerCase() + "_jira_versions.json", rawJiraRel);

        // 4) Fetch e dump dei tag GitHub
        List<String> gitTags = PipelineUtils.fetchGitHubTags(cfg.owner(), cfg.repo());
        dumpJson(cacheDir, cfg.repo().toLowerCase() + "_git_tags.json", gitTags);

        // 5) Calcola l’intersezione JIRA↔Git, dump e log
        Set<String> jiraNorm = rawJiraRel.stream()
                .map(v -> PipelineUtils.normalize(v.getName()))
                .collect(Collectors.toSet());
        List<String> releases = gitTags.stream()
                .filter(t -> jiraNorm.contains(PipelineUtils.normalize(t)))
                .sorted(PipelineUtils::compareSemver)
                .collect(Collectors.toList());
        if (releases.isEmpty()) {
            releases = List.of("HEAD");
        }
        dumpJson(cacheDir, cfg.repo().toLowerCase() + "_releases_intersection.json", releases);
        LOG.info("Release da elaborare per {}: {}", cfg.repo(), releases);

        // 6) Clona/Aggiorna repository
        new GitInjection(
                PipelineUtils.repoRemoteUrl(cfg.localPath()),
                cfg.localPath().toFile(),
                new ArrayList<>()
        ).injectCommits();

        // 7) Calcolo buggy-info (col nuovo computeOrLoad)
        BuggyInfo bugInfo = BuggyMethodExtractor.computeOrLoad(
                cfg.localPath().toFile(),
                tickets,
                cfg.repo().toLowerCase(),
                cacheDir
        );

        // 8) Feature extraction e CSV
        FeatureExtractor fx = new FeatureExtractor();
        String csvBase = "dataset_" + cfg.repo().toLowerCase() + ".csv";
        boolean first = true;
        for (String tag : releases) {
            LOG.info("   • elaboro {}@{}", cfg.repo(), tag);
            Map<File, Map<String, FeatureExtractor.MethodFeatures>> feats;
            if ("HEAD".equals(tag)) {
                feats = PipelineUtils.walkAndExtract(
                        cfg.localPath().toFile(), fx, DEFAULT_FILTERS
                );
            } else {
                Path tmp = PipelineUtils.downloadAndUnzip(cfg.owner(), cfg.repo(), tag);
                Path proj = PipelineUtils.findSingleSubdir(tmp);
                feats = PipelineUtils.walkAndExtract(proj.toFile(), fx, DEFAULT_FILTERS);
                PipelineUtils.deleteDirectoryRecursively(tmp);
            }
            new CsvGenerator(tag, !first)
                    .generateCsv(feats, bugInfo, csvBase);
            first = false;
        }

        // 9) Dedup + filtro + riduzione cross-release
        Path raw      = Paths.get(csvBase);
        Path dedup    = Paths.get("dataset_" + cfg.repo() + "_dedup.csv");
        Path filtered = Paths.get("dataset_" + cfg.repo() + "_filtered.csv");
        Path finalCsv = Paths.get(cfg.repo() + "_dataset_finale.csv");

        CsvDeduplicator.deduplicate(raw, dedup);
        if (cfg.releaseCut() != null) {
            CsvDeduplicator.dedupAndFilterUpTo(dedup, filtered, cfg.releaseCut());
        } else {
            Files.copy(dedup, filtered, StandardCopyOption.REPLACE_EXISTING);
        }
        FinalCsvReducer.reduceDuplicates(filtered, finalCsv);

        LOG.info("✅ Pipeline {} completata, output: {}", cfg.repo(), finalCsv.toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static List<JiraTicket> loadOrDownloadTickets(
            BookkeeperFetcher fetcher,
            ProjectConfig cfg,
            Path cacheDir
    ) throws Exception {
        Path json = cacheDir.resolve(cfg.repo().toLowerCase() + "_jira_tickets.json");
        Jsonb jb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));

        if (Files.exists(json)) {
            LOG.info("▶ Carico cache ticket da {}", json);
            try (Reader r = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
                Type t = new ArrayList<JiraTicket>() {}.getClass().getGenericSuperclass();
                return (List<JiraTicket>) jb.fromJson(r, t);
            }
        }

        LOG.info("▶ Download ticket JIRA per {}", cfg.repo());
        List<JiraTicket> ts = fetcher.fetchAllJiraTickets(
                System.getenv("JIRA_USER"), System.getenv("JIRA_PASS")
        );
        fetcher.writeTicketsToJsonFile(ts, json.toString());
        LOG.info("✅ Ticket salvati in {}", json);
        return ts;
    }

    private static void dumpJson(Path dir, String filename, Object data) throws Exception {
        Path file = dir.resolve(filename);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));
            jsonb.toJson(data, w);
        }
        LOG.info("✅ Dump salvato in {}", file);
    }
}
