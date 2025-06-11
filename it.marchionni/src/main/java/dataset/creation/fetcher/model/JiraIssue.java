package dataset.creation.fetcher.model;

public class JiraIssue {
    private final String key;
    private final String summary;
    private final String status;
    private final String fixVersion;

    public JiraIssue(String key, String summary, String status, String fixVersion) {
        this.key        = key;
        this.summary    = summary;
        this.status     = status;
        this.fixVersion = fixVersion;
    }

    public String getKey()        { return key; }
    public String getSummary()    { return summary; }
    public String getStatus()     { return status; }
    public String getFixVersion() { return fixVersion; }
}
