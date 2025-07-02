package dataset.creation.fetcher.jira;

import java.util.List;

public class JiraSearchResponse {
    private int total;
    private List<JiraSearchIssue> issues;

    // default constructor per JSON-B
    public JiraSearchResponse() {
        //empty
    }
    public int getTotal() {
        return total;
    }
    public void setTotal(int total) {
        this.total = total;
    }

    public List<JiraSearchIssue> getIssues() {
        return issues;
    }
    public void setIssues(List<JiraSearchIssue> issues) {
        this.issues = issues;
    }
}

