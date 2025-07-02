package dataset.creation.fetcher.fields;

public class IssueType implements FieldWithName {
    private String name;
    public IssueType() {
        //empty
    }
    @Override public String getName() { return name; }
}
