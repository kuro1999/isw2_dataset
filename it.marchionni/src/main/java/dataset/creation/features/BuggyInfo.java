package dataset.creation.features;

import java.util.*;

import dataset.creation.features.metrics.MethodMetrics;
import jakarta.json.bind.annotation.JsonbProperty;

/**
 * Versione mutabile di BuggyInfo, con costruttore di default e setter.
 */
public class BuggyInfo {
    // rimuoviamo final e usiamo campi protetti o pubblici
    @JsonbProperty("buggyMethods")
    private Set<String> buggyMethods = new HashSet<>();

    @JsonbProperty("metricsByMethod")
    private Map<String, MethodMetrics> metricsByMethod = new HashMap<>();

    /** costruttore di default per JSON-B */
    public BuggyInfo() { }

    // costruttore di convenienza
    public BuggyInfo(Set<String> buggyMethods,
                     Map<String, MethodMetrics> metricsByMethod) {
        this.buggyMethods    = buggyMethods;
        this.metricsByMethod = metricsByMethod;
    }

    // getter + setter
    public Set<String> getBuggyMethods() {
        return buggyMethods;
    }
    public void setBuggyMethods(Set<String> buggyMethods) {
        this.buggyMethods = buggyMethods;
    }

    public Map<String, MethodMetrics> getMetricsByMethod() {
        return metricsByMethod;
    }
    public void setMetricsByMethod(Map<String, MethodMetrics> metricsByMethod) {
        this.metricsByMethod = metricsByMethod;
    }

    /** comodo per recuperare direttamente le metriche di un singolo metodo */
    public MethodMetrics getMetricsFor(String methodName) {
        return metricsByMethod.get(methodName);
    }
}
