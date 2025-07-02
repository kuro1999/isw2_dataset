package dataset.creation.fetcher.fields;

public class Fields {
    private String summary;
    private String description;
    private Status status;
    private IssueType issuetype;
    private Priority priority;
    private User reporter;
    private User assignee;
    private Resolution resolution;

    public Fields() {
        //empty
    }
    public String getSummary() {
        return summary;
    }
    public void setSummary(String summary) {
        this.summary = summary;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public Status getStatus() {
        return status;
    }
    public void setStatus(Status status) {
        this.status = status;
    }
    public IssueType getIssuetype() {
        return issuetype;
    }
    public void setIssuetype(IssueType issuetype) {
        this.issuetype = issuetype;
    }
    public Priority getPriority() {
        return priority;
    }
    public void setPriority(Priority priority) {
        this.priority = priority;
    }
    public User getReporter() {
        return reporter;
    }
    public void setReporter(User reporter) {
        this.reporter = reporter;
    }
    public User getAssignee() {
        return assignee;
    }
    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }
    public Resolution getResolution() {
        return resolution;
    }
    public void setResolution(Resolution resolution) {
        this.resolution = resolution;
    }
}