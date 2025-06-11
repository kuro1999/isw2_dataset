package dataset.creation.fetcher.model;

import jakarta.json.bind.annotation.JsonbTransient;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.Instant;

/**
 * Wrapper minimale di RevCommit che eviti ricorsioni JSON.
 */
public class Commit {

    @JsonbTransient           // non serializziamo l’intero RevCommit
    private final RevCommit revCommit;

    @JsonbTransient           // per evitare cicli Release → Commit → Release
    private final Release   release;

    private final String  hash;
    private final String  message;
    private final Instant date;

    public Commit(RevCommit revCommit, Release release) {
        this.revCommit = revCommit;
        this.release   = release;
        this.hash      = revCommit.getName();
        this.message   = revCommit.getShortMessage();
        this.date      = revCommit.getCommitterIdent().getWhenAsInstant();
    }

    /* ---- getter “visibili” nel JSON ---- */
    public String  getHash()    { return hash; }
    public String  getMessage() { return message; }
    public Instant getDate()    { return date; }
    /* ---- getter interni ---- */
    public RevCommit getRevCommit() { return revCommit; }
    public Release   getRelease()   { return release; }
}
