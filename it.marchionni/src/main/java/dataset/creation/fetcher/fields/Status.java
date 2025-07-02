package dataset.creation.fetcher.fields;

public class Status implements FieldWithName {
    private String name;
    public Status() {
        // empty
    }
    @Override public String getName() { return name; }
}
