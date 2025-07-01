package dataset.creation.features.metrics;

// 2. Metriche di complessità / storico
public class ComplexityMetrics {
    private int historyCount;
    private int authorCount;

    public ComplexityMetrics(int historyCount, int authorCount) {
        this.historyCount = historyCount;
        this.authorCount  = authorCount;
    }
    public int getHistoryCount() {
        return historyCount;
    }
    public int getAuthorCount() {
        return authorCount;
    }
    public void setHistoryCount(int historyCount) {
        this.historyCount = historyCount;
    }
    public void setAuthorCount(int authorCount) {
        this.authorCount = authorCount;
    }
}
