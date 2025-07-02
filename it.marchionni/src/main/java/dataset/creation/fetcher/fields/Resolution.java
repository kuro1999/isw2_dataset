package dataset.creation.fetcher.fields;

public class Resolution implements FieldWithName {
    private String name;
    public Resolution() {
        //empty
    }
    @Override public String getName() {
        return name;
    }
}
