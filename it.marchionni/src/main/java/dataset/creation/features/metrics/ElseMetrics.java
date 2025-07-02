package dataset.creation.features.metrics;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

// 3. Metriche sugli “else”
public class ElseMetrics {
    private int elseAdded;
    private int elseDeleted;

    @JsonbCreator
    public ElseMetrics(
            @JsonbProperty("elseAdded")   int elseAdded,
            @JsonbProperty("elseDeleted") int elseDeleted
    ) {
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
