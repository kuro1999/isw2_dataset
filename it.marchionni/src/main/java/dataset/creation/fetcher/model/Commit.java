package dataset.creation.fetcher.model;

import jakarta.json.bind.annotation.JsonbTransient;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.Instant;

public class Commit {

    @JsonbTransient
    private final RevCommit revCommit;

    /** riferimento leggero, non la classe completa → niente ciclo */
    private final ReleaseInfo release;

    private final String  hash;
    private final String  message;
    private final Instant date;

    public Commit(RevCommit revCommit, ReleaseInfo release) {
        this.revCommit = revCommit;
        this.release   = release;
        this.hash      = revCommit.getName();
        this.message   = revCommit.getShortMessage();
        this.date      = revCommit.getCommitterIdent().getWhenAsInstant();
    }

    /* ---- getter visibili in JSON ---- */
    public String  getHash()        { return hash; }
    public String  getMessage()     { return message; }
    public Instant getDate()        { return date; }
    public String  getReleaseTag()  { return release.getTag(); }   // solo il tag
    /* ---- getter interni ---- */
    @JsonbTransient
    public RevCommit getRevCommit() { return revCommit; }
}
