package dataset.creation.fetcher.model;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Release {

    /* ------------------------ campi serializzati ------------------------ */
    private int       id;           // viene settato in GitInjection
    private String    tag;
    private LocalDate releaseDate;

    /* ------------------------ campi NON serializzati ------------------- */
    @JsonbTransient                  // evitiamo JSON giganteschi / ricorsioni
    private final List<Commit> commitList = new ArrayList<>();

    /* ------------------------ costruttori ------------------------------ */
    public Release() {}   // richiesto da JSON-B

    public Release(String tag, Instant publishedAt) {
        this.tag         = tag;
        this.releaseDate = publishedAt.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /* ------------------------ API helper ------------------------------- */
    public void addCommit(Commit c) { commitList.add(c); }

    @JsonbProperty("commitCount")    // <-- apparirà nel JSON
    public int getCommitCount() { return commitList.size(); }

    /* getter della lista per uso interno (NON in JSON) */
    public List<Commit> getCommitList() {
        return Collections.unmodifiableList(commitList);
    }

    /* ------------------------ getter / setter --------------------------- */
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Mappiamo `tag` su JSON come proprietà "name"
    @JsonbProperty("name")
    public String getTag() { return tag; }
    @JsonbProperty("name")
    public void setTag(String tag) { this.tag = tag; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
}