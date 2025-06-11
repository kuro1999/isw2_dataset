package dataset.creation.fetcher.model;

import java.time.LocalDate;

/**
 * Rappresenta una versione JIRA con
 *  - id numerico interno
 *  - nome (es. "4.14.0")
 *  - data di rilascio
 *
 *  Servono i setter perché il parsing JSON-B (e il tuo
 *  JiraInjection.java) li usa per iniettare i valori.
 */
public class JiraVersion {

    /* ---------- campi ---------- */
    private int       id;
    private String    name;
    private LocalDate releaseDate;

    /* ---------- costruttori ---------- */
    public JiraVersion() { }   // richiesto da JSON-B

    public JiraVersion(String name, LocalDate releaseDate) {
        this.name        = name;
        this.releaseDate = releaseDate;
    }

    public JiraVersion(int id, String name, LocalDate releaseDate) {
        this.id          = id;
        this.name        = name;
        this.releaseDate = releaseDate;
    }

    /* ---------- getter / setter ---------- */
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
}
