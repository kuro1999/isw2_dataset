package dataset.creation.fetcher.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Modello “ricco” di ticket JIRA.
 * Tutti i campi sono opzionali: se l'API non li restituisce restano null o [].
 */
public class JiraTicket {

    /* ---------- identificazione ---------- */
    private String key;
    private String summary;
    private String description;
    private String status;          // es. "Closed"
    private String issueType;       // es. "Bug"

    /* ---------- date ---------- */
    private LocalDate creationDate;
    private LocalDate resolutionDate;
    private LocalDate updatedDate;

    /* ---------- versioni ---------- */
    private JiraVersion openingVersion;
    private JiraVersion fixedVersion;
    private List<JiraVersion> affectedVersions = new ArrayList<>();

    /* ---------- metadati ---------- */
    private String priority;
    private String reporter;
    private String assignee;
    private String resolution;      // es. "Fixed"

    private List<String> labels     = new ArrayList<>();
    private List<String> components = new ArrayList<>();

    /* costruttore vuoto per JSON-B */
    public JiraTicket() {}

    /* ---------- getter & setter ---------- */
    public String  getKey() { return key; }
    public void    setKey(String key) { this.key = key; }

    public String  getSummary() { return summary; }
    public void    setSummary(String summary) { this.summary = summary; }

    public String  getDescription() { return description; }
    public void    setDescription(String description) { this.description = description; }

    public String  getStatus() { return status; }
    public void    setStatus(String status) { this.status = status; }

    public String  getIssueType() { return issueType; }
    public void    setIssueType(String issueType) { this.issueType = issueType; }

    public LocalDate getCreationDate() { return creationDate; }
    public void      setCreationDate(LocalDate creationDate) { this.creationDate = creationDate; }

    public LocalDate getResolutionDate() { return resolutionDate; }
    public void      setResolutionDate(LocalDate resolutionDate) { this.resolutionDate = resolutionDate; }

    public LocalDate getUpdatedDate() { return updatedDate; }
    public void      setUpdatedDate(LocalDate updatedDate) { this.updatedDate = updatedDate; }

    public JiraVersion getOpeningVersion() { return openingVersion; }
    public void        setOpeningVersion(JiraVersion openingVersion) { this.openingVersion = openingVersion; }

    public JiraVersion getFixedVersion() { return fixedVersion; }
    public void        setFixedVersion(JiraVersion fixedVersion) { this.fixedVersion = fixedVersion; }

    public List<JiraVersion> getAffectedVersions() { return affectedVersions; }
    public void              setAffectedVersions(List<JiraVersion> affectedVersions) {
        this.affectedVersions = affectedVersions;
    }

    public String  getPriority() { return priority; }
    public void    setPriority(String priority) { this.priority = priority; }

    public String  getReporter() { return reporter; }
    public void    setReporter(String reporter) { this.reporter = reporter; }

    public String  getAssignee() { return assignee; }
    public void    setAssignee(String assignee) { this.assignee = assignee; }

    public String  getResolution() { return resolution; }
    public void    setResolution(String resolution) { this.resolution = resolution; }

    public List<String> getLabels() { return labels; }
    public void        setLabels(List<String> labels) { this.labels = labels; }

    public List<String> getComponents() { return components; }
    public void        setComponents(List<String> components) { this.components = components; }
}
