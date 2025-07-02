package dataset.creation.fetcher;

import dataset.creation.exceptions.GitInjectionException;
import dataset.creation.fetcher.model.Commit;
import dataset.creation.fetcher.model.Release;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Clona (o apre) il repo Git di BookKeeper, deduplica i commit di tutti
 * i branch e li assegna alla release successiva in ordine cronologico.
 *
 * Ogni {@link Release} mantiene internamente una commitList, quindi
 * {@code getCommitCount()} restituisce la numerosità senza
 * serializzare l'intero elenco.
 */
public class GitInjection {

    private static final Logger log = LoggerFactory.getLogger(GitInjection.class);

    /** rimane per compatibilità con altre classi che lo importano */
    public static final String LOCAL_DATE_FORMAT = "yyyy-MM-dd";

    private final Git git;
    private final Repository repo;
    private final List<Release> releases;
    private List<Commit> commits;

    public GitInjection(String remoteUrl, File workDir, List<Release> releases)
            throws GitInjectionException {
        this.releases = releases;
        try {
            if (workDir.exists()) {
                log.info("→ repository locale già presente, eseguo fetch…");
                git = Git.open(workDir);
                git.fetch().setRemoveDeletedRefs(true).call();
            } else {
                log.info("→ clono {} in {}", remoteUrl, workDir.getPath());
                git = Git.cloneRepository()
                        .setURI(remoteUrl)
                        .setDirectory(workDir)
                        .call();
            }
            repo = git.getRepository();
        } catch (Exception e) {
            throw new GitInjectionException(
                    "Errore inizializzazione GitInjection per URL: " + remoteUrl, e);
        }
    }


    public void injectCommits() throws GitInjectionException {
        try {
            // 1. raccolta e ordinamento
            List<RevCommit> revCommits = collectUniqueSortedCommits();
            // 2. assegnazione alle release
            assignCommitsToReleases(revCommits);
            // 3+4. pulizia, id progressivi e log finale
            finalizeReleasesAndLog();
        } catch (Exception e) {
            throw new GitInjectionException("Errore durante injectCommits()", e);
        }
    }

    private List<RevCommit> collectUniqueSortedCommits() throws GitInjectionException {
        try {
            List<RevCommit> revCommits = new ArrayList<>();
            for (Ref br : git.branchList()
                    .setListMode(ListBranchCommand.ListMode.ALL)
                    .call()) {

                for (RevCommit rc : git.log().add(repo.resolve(br.getName())).call()) {
                    if (!revCommits.contains(rc)) {
                        revCommits.add(rc);
                    }
                }
            }
            revCommits.sort(Comparator.comparing(
                    rc -> rc.getCommitterIdent().getWhenAsInstant()));
            log.info("→ {} commit unici trovati", revCommits.size());
            return revCommits;
        }catch (Exception e) {
            throw  new GitInjectionException("Errore durante collectUniqueSortedCommits()", e);
        }
    }

    private void assignCommitsToReleases(List<RevCommit> revCommits) {
        commits = new ArrayList<>();
        for (RevCommit rc : revCommits) {
            LocalDate commitDate = toUtcLocalDate(
                    rc.getCommitterIdent().getWhenAsInstant());
            LocalDate lower = LocalDate.MIN;

            for (Release rel : releases) {
                LocalDate relDate = rel.getReleaseDate();
                if (commitDate.isAfter(lower) && !commitDate.isAfter(relDate)) {
                    Commit c = new Commit(rc, rel);
                    commits.add(c);
                    rel.addCommit(c);
                }
                lower = relDate;
            }
        }
    }

    private void finalizeReleasesAndLog() {
        // rimuovi release vuote e assegna id
        releases.removeIf(r -> r.getCommitList().isEmpty());
        int id = 0;
        for (Release r : releases) {
            r.setId(++id);
        }
        // ordina commits
        commits.sort(Comparator.comparing(c ->
                c.getRevCommit().getCommitterIdent().getWhenAsInstant()));
        // log riepilogo
        log.info("→ assegnati {} commit a {} release",
                commits.size(), releases.size());
        for (Release r : releases) {
            log.info("   • {} : {} commit",
                    r.getTag(), r.getCommitCount());
        }
    }


    /** Helper: Instant → LocalDate (UTC) */
    private static LocalDate toUtcLocalDate(Instant inst) {
        return inst.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** Getter pubblici */
    public List<Commit> getCommits() {
        return commits;
    }
    public List<Release> getReleases() {
        return releases;
    }
}
