package dataset.creation.fetcher.model;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modello di release Git/Jira.
 * Implementa ReleaseInfo così Commit dipende solo da questa interfaccia
 * (e non vice-versa) eliminando qualsiasi ciclo di dipendenze.
 */
public class Release implements ReleaseInfo {

    /* -------------------- campi serializzati --------------------------- */
    private int       id;            // assegnato da GitInjection
    private String    tag;           // es. "release-4.5.0"
    private LocalDate releaseDate;

    /* -------------------- campi NON serializzati ----------------------- */
    @JsonbTransient
    private final List<Commit> commitList = new ArrayList<>();

    /* -------------------- costruttori --------------------------------- */
    public Release() {
        /* richiesto da JSON-B */
    }

    public Release(String tag, Instant publishedAt) {
        this.tag         = tag;
        this.releaseDate = publishedAt.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /* -------------------- helper API ---------------------------------- */
    public void addCommit(Commit c) {
        commitList.add(c);
    }

    @JsonbProperty("commitCount")
    public int getCommitCount() {
        return commitList.size();
    }

    /** lista solo per uso interno (non serializzata) */
    public List<Commit> getCommitList() {
        return Collections.unmodifiableList(commitList);
    }

    /* -------------------- getter / setter ----------------------------- */
    public int getId()               { return id; }
    public void setId(int id)        { this.id = id; }

    /** `tag` esposto/accettato come proprietà JSON "name" */
    @JsonbProperty("name")
    @Override
    public String getTag()           { return tag; }
    @JsonbProperty("name")
    public void setTag(String tag)   { this.tag = tag; }

    @Override
    public LocalDate getReleaseDate(){ return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }
}