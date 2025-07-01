package dataset.creation.features.metrics;

// 3. Metriche sugli “else”
public class ElseMetrics {
    private int elseAdded;
    private int elseDeleted;

    public ElseMetrics(int elseAdded, int elseDeleted) {
        this.elseAdded   = elseAdded;
        this.elseDeleted = elseDeleted;
    }
    public int getElseAdded() {
        return elseAdded;
    }
    public int getElseDeleted() {
        return elseDeleted;
    }
    public void setElseAdded(int elseAdded) {
        this.elseAdded   = elseAdded;
    }
    public void setElseDeleted(int elseDeleted) {
        this.elseDeleted   = elseDeleted;
    }
}
