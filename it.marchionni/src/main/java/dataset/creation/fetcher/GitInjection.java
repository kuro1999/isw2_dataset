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
 * Clona (o apre) il repo Git, deduplica i commit di tutti i branch
 * e li assegna alla release successiva in ordine cronologico.
 */
public class GitInjection {

    private static final Logger log = LoggerFactory.getLogger(GitInjection.class);

    public static final String LOCAL_DATE_FORMAT = "yyyy-MM-dd";

    private final Git         git;
    private final Repository  repo;
    private final List<Release> releases;
    private       List<Commit> commits;

    /* --------------------------------------------------------------- */
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

    /* --------------------------------------------------------------- */
    public void injectCommits() throws GitInjectionException {
        try {
            List<RevCommit> revCommits = collectUniqueSortedCommits();
            assignCommitsToReleases(revCommits);
            finalizeReleasesAndLog();
        } catch (Exception e) {
            throw new GitInjectionException("Errore durante injectCommits()", e);
        }
    }

    /* --------------------------------------------------------------- */
    private List<RevCommit> collectUniqueSortedCommits() throws GitInjectionException {
        try {
            List<RevCommit> result = new ArrayList<>();

            for (Ref br : git.branchList()
                    .setListMode(ListBranchCommand.ListMode.ALL)
                    .call()) {

                for (RevCommit rc : git.log().add(repo.resolve(br.getName())).call()) {
                    if (!result.contains(rc)) {
                        result.add(rc);
                    }
                }
            }
            result.sort(Comparator.comparing(rc -> rc.getCommitterIdent()
                    .getWhenAsInstant()));

            log.info("→ {} commit unici trovati", result.size());
            return result;

        } catch (Exception e) {
            throw new GitInjectionException("Errore durante la raccolta dei commit", e);
        }
    }

    /* --------------------------------------------------------------- */
    private void assignCommitsToReleases(List<RevCommit> revCommits) {

        commits = new ArrayList<>();

        for (RevCommit rc : revCommits) {
            LocalDate commitDate = toUtcLocalDate(rc.getCommitterIdent()
                    .getWhenAsInstant());
            LocalDate lower = LocalDate.MIN;

            for (Release rel : releases) {

                LocalDate relDate = rel.getReleaseDate();
                if (commitDate.isAfter(lower) && !commitDate.isAfter(relDate)) {

                    /* rel implementa ReleaseInfo → nessuna dipendenza circolare */
                    Commit c = new Commit(rc, rel);

                    commits.add(c);
                    rel.addCommit(c);
                }
                lower = relDate;
            }
        }
    }

    /* --------------------------------------------------------------- */
    private void finalizeReleasesAndLog() {

        // rimuove release senza commit e assegna id progressivi
        releases.removeIf(r -> r.getCommitList().isEmpty());
        int id = 0;
        for (Release r : releases) {
            r.setId(++id);
        }

        // ordina commit per data
        commits.sort(Comparator.comparing(c ->
                c.getRevCommit().getCommitterIdent().getWhenAsInstant()));

        // log riepilogativo
        log.info("→ assegnati {} commit a {} release", commits.size(), releases.size());
        for (Release r : releases) {
            log.info("   • {} : {} commit", r.getTag(), r.getCommitCount());
        }
    }

    /* --------------------------------------------------------------- */
    private static LocalDate toUtcLocalDate(Instant inst) {
        return inst.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /* ---------------- getter pubblici ------------------------------ */
    public List<Commit>  getCommits()  { return commits; }
    public List<Release> getReleases() { return releases; }
}
