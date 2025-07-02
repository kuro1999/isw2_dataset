package dataset.creation.features.metrics;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

// 4. Metriche di add/delete
public class AddDeleteMetrics {
    private double avgAdded;
    private int maxAdded;
    private double avgDeleted;
    private int maxDeleted;

    @JsonbCreator
    public AddDeleteMetrics(
            @JsonbProperty("avgAdded")   double avgAdded,
            @JsonbProperty("maxAdded")   int    maxAdded,
            @JsonbProperty("avgDeleted") double avgDeleted,
            @JsonbProperty("maxDeleted") int    maxDeleted
    ) {
        this.avgAdded   = avgAdded;
        this.maxAdded   = maxAdded;
        this.avgDeleted = avgDeleted;
        this.maxDeleted = maxDeleted;
    }
    public double getAvgAdded() {
        return avgAdded;
    }
    public int getMaxAdded() {
        return maxAdded;
    }
    public double getAvgDeleted() {
        return avgDeleted;
    }
    public int getMaxDeleted() {
        return maxDeleted;
    }
    public void setAvgAdded(double avgAdded) {
        this.avgAdded = avgAdded;
    }
    public void setMaxAdded(int maxAdded) {
        this.maxAdded = maxAdded;
    }
    public void setAvgDeleted(double avgDeleted) {
        this.avgDeleted = avgDeleted;
    }
    public void setMaxDeleted(int maxDeleted) {
        this.maxDeleted = maxDeleted;
    }
}
