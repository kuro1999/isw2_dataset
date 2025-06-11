package dataset.creation.fetcher;

import dataset.creation.fetcher.model.Commit;
import dataset.creation.fetcher.model.Release;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
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

    /** rimane per compatibilità con altre classi che lo importano */
    public static final String LOCAL_DATE_FORMAT = "yyyy-MM-dd";

    /* ------------------------------------------------------------------ */
    private final Git        git;
    private final Repository repo;
    private final List<Release> releases;
    private       List<Commit> commits;

    /* ------------------------------------------------------------------ */
    public GitInjection(String remoteUrl, File workDir, List<Release> releases) throws Exception {
        this.releases = releases;

        if (workDir.exists()) {
            System.out.println("→ repository locale già presente, eseguo fetch…");
            git  = Git.open(workDir);
            git.fetch().setRemoveDeletedRefs(true).call();
        } else {
            System.out.println("→ clono " + remoteUrl + " in " + workDir.getPath());
            git = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(workDir)
                    .call();
        }
        repo = git.getRepository();
    }

    /* ==================================================================
       COMMIT ↔ RELEASE
       ================================================================== */
    public void injectCommits() throws Exception {

        /* 1. raccogli TUTTI i commit di tutti i branch (senza duplicati) */
        List<RevCommit> revCommits = new ArrayList<>();
        for (Ref br : git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call()) {

            Iterable<RevCommit> branchCommits =
                    git.log().add(repo.resolve(br.getName())).call();

            for (RevCommit rc : branchCommits)
                if (!revCommits.contains(rc))
                    revCommits.add(rc);
        }
        revCommits.sort(Comparator.comparing(rc -> rc.getCommitterIdent().getWhenAsInstant()));
        System.out.printf("→ %d commit unici trovati%n", revCommits.size());

        /* 2. assegna ogni commit alla release “successiva” per data */
        commits = new ArrayList<>();
        for (RevCommit rc : revCommits) {

            LocalDate commitDate = toUtcLocalDate(rc.getCommitterIdent().getWhenAsInstant());
            LocalDate lowerBound = LocalDate.MIN;

            for (Release rel : releases) {
                LocalDate relDate = rel.getReleaseDate();
                if (commitDate.isAfter(lowerBound) && !commitDate.isAfter(relDate)) {
                    Commit c = new Commit(rc, rel);
                    commits.add(c);
                    rel.addCommit(c);
                }
                lowerBound = relDate;
            }
        }

        /* 3. rimuovi release senza commit e assegna id progressivi */
        releases.removeIf(r -> r.getCommitList().isEmpty());
        int id = 0;
        for (Release r : releases) r.setId(++id);

        commits.sort(Comparator.comparing(c ->
                c.getRevCommit().getCommitterIdent().getWhenAsInstant()));

        /* 4. log riepilogativo */
        System.out.printf("→ assegnati %d commit a %d release%n",
                commits.size(), releases.size());
        releases.forEach(r ->
                System.out.printf("   • %s : %d commit%n",
                        r.getTag(), r.getCommitCount()));
    }

    /* ------------------------------------------------------------------ */
    /* Helper: Instant → LocalDate (UTC)                                  */
    /* ------------------------------------------------------------------ */
    private static LocalDate toUtcLocalDate(Instant inst) {
        return inst.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /* ------------------------------------------------------------------ */
    /* Getter pubblici                                                    */
    /* ------------------------------------------------------------------ */
    public List<Commit>  getCommits()  { return commits; }
    public List<Release> getReleases() { return releases; }
}
