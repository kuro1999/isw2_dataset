package dataset.creation.features.metrics;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

// 1. Metriche sui cambi strutturali
public class StructuralChangeMetrics {
    private int churn;
    private double avgChurn;
    private int maxChurn;
    private int condChanges;

    @JsonbCreator
    public StructuralChangeMetrics(
            @JsonbProperty("churn")       int churn,
            @JsonbProperty("avgChurn")    double avgChurn,
            @JsonbProperty("maxChurn")    int maxChurn,
            @JsonbProperty("condChanges") int condChanges
    ) {
        this.churn       = churn;
        this.avgChurn    = avgChurn;
        this.maxChurn    = maxChurn;
        this.condChanges = condChanges;
    }

    public int getChurn() {
        return churn;
    }
    public double getAvgChurn() {
        return avgChurn;
    }
    public int getMaxChurn() {
        return maxChurn;
    }
    public int getCondChanges() {
        return condChanges;
    }
    public void setChurn(int churn) {
        this.churn=churn;
    }
    public void setAvgChurn(double avgChurn) {
        this.avgChurn = avgChurn;
    }
    public void setMaxChurn(int maxChurn) {
        this.maxChurn = maxChurn;
    }
    public void setCondChanges(int condChanges) {
        this.condChanges = condChanges;
    }


}
