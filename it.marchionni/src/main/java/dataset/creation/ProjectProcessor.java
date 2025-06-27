// src/main/java/dataset/creation/ProjectProcessor.java
package dataset.creation;

import dataset.creation.fetcher.*;
import dataset.creation.fetcher.model.*;
import dataset.creation.features.*;
import dataset.creation.utils.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectProcessor {
    private static final Logger log = LoggerFactory.getLogger(ProjectProcessor.class);
    private final ProjectConfig cfg;

    public ProjectProcessor(ProjectConfig cfg) {
        this.cfg = cfg;
    }

    /** Esegue l’intero pipeline per un singolo progetto. */
    public void run() throws Exception {
        log.info("🔸 Avvio pipeline per {}", cfg.repo());

        /* 0. credenziali */
        cfg.extraEnv().forEach(System::setProperty);

        /* 1. Ticket / release JIRA */
        JiraInjection jira = new JiraInjection(cfg.jiraProject());
        jira.injectReleases();
        List<JiraVersion> jiraRel = jira.getReleases();

        /* 2. Tag GitHub */
        List<String> gitTags = Main.fetchGitHubTags(cfg.owner(), cfg.repo());

        /* 3. Intersezione */
        Set<String> jiraNorm = jiraRel.stream()
                .map(v -> Main.normalize(v.getName()))
                .collect(Collectors.toSet());
// da  Stream … .sorted(Main::compareSemver).toList();
        List<String> releases = gitTags.stream()
                .filter(t -> jiraNorm.contains(Main.normalize(t)))
                .sorted(Main::compareSemver)
                .collect(Collectors.toList());   // <— Java 11

        if (releases.isEmpty()) releases = List.of("HEAD");

        /* 4. Assicurati che la clone locale esista/si aggiorni */
        GitInjection gitInj = new GitInjection(
                Main.repoRemoteUrl(cfg.localPath()), cfg.localPath().toFile(), new ArrayList<>()
        );
        gitInj.injectCommits();

        /* 5. Buggy / feature extraction */
        BookkeeperFetcher fetcher = new BookkeeperFetcher();            // riusa fetcher generico
        List<JiraTicket> tickets  = Main.loadOrDownloadTickets(fetcher, cfg.repo());
        BuggyMethodExtractor.BuggyInfo bugInfo         = BuggyMethodExtractor.computeOrLoad(
                cfg.localPath().toFile(), tickets);

        FeatureExtractor fx = new FeatureExtractor();

        /* 6. Genera CSV per ogni release */
        String baseCsv   = "dataset_" + cfg.repo() + ".csv";
        boolean firstCsv = true;
        for (String tag : releases) {
            Map<File, Map<String, FeatureExtractor.MethodFeatures>> feats;
            if ("HEAD".equals(tag)) {
                feats = Main.walkAndExtract(cfg.localPath().toFile(), fx);
            } else {
                Path zipTmp = Main.downloadAndUnzip(cfg.owner(), cfg.repo(), tag);
                Path proj   = Main.findSingleSubdir(zipTmp);
                feats       = Main.walkAndExtract(proj.toFile(), fx);
                Main.deleteDirectoryRecursively(zipTmp);
            }
            new CsvGenerator(tag, !firstCsv)
                    .generateCsv(feats, bugInfo, baseCsv);
            firstCsv = false;
        }

        /* 7. Dedup e cut CSV */
        Path raw  = Paths.get(baseCsv);
        Path ded  = Paths.get("dataset_" + cfg.repo() + "_dedup.csv");
        CsvDeduplicator.deduplicate(raw, ded);

        Path filt = Paths.get("dataset_" + cfg.repo() + "_filtered.csv");
        if (cfg.releaseCut() != null)
            CsvDeduplicator.dedupAndFilterUpTo(ded, filt, cfg.releaseCut());
        else
            Files.copy(ded, filt, StandardCopyOption.REPLACE_EXISTING);

        /* 8. Riduzione cross-release */
        Path finalCsv = Paths.get(cfg.repo() + "_dataset_finale.csv");
        FinalCsvReducer.reduceDuplicates(filt, finalCsv);

        log.info("✅ Pipeline {} terminata - output {}", cfg.repo(), finalCsv.toAbsolutePath());
    }
}
