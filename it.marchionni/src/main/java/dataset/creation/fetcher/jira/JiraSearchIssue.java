package dataset.creation.fetcher.jira;

import dataset.creation.fetcher.fields.Fields;

public class JiraSearchIssue {
    private String key;
    private Fields fields;

    public JiraSearchIssue() {
        //empty
    }
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public Fields getFields() {
        return fields;
    }
    public void setFields(Fields fields) {
        this.fields = fields;
    }
}
