package dataset.creation.features.metrics;

// 1. Metriche sui cambi strutturali
public class StructuralChangeMetrics {
    private int churn;
    private double avgChurn;
    private int maxChurn;
    private int condChanges;

    public StructuralChangeMetrics(int churn, double avgChurn, int maxChurn, int condChanges) {
        this.churn        = churn;
        this.avgChurn     = avgChurn;
        this.maxChurn     = maxChurn;
        this.condChanges  = condChanges;
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
