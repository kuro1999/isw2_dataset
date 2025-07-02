package dataset.creation.features.metrics;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

// 2. Metriche di complessità / storico
public class ComplexityMetrics {
    private int historyCount;
    private int authorCount;

    @JsonbCreator
    public ComplexityMetrics(
            @JsonbProperty("historyCount") int historyCount,
            @JsonbProperty("authorCount")  int authorCount
    ) {
        this.historyCount = historyCount;
        this.authorCount  = authorCount;
    }
    public int getHistoryCount() {
        return historyCount;
    }
    public int getAuthorCount() {
        return authorCount;
    }
}
