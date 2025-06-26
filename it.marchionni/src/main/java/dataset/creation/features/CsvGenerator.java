package dataset.creation.features;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.Set;

/**
 * Genera CSV includendo:
 *  - static metrics (LOC, complexity, code smells, ecc.)
 *  - churn metrics (total, avg, max)
 *  - change details (elseAdded, elseDeleted, condChanges)
 *  - history details (n° commit, n° autori)
 *  - label buggy
 */
public class CsvGenerator {

    private final String version;
    private final boolean append;

    public CsvGenerator(String version, boolean append) {
        this.version = version;
        this.append = append;
    }

    public void generateCsv(
            Map<File, Map<String, FeatureExtractor.MethodFeatures>> featuresPerFile,
            BuggyMethodExtractor.BuggyInfo info,
            String outputCsv
    ) throws Exception {

        // Header extended with new columns
        String[] headers = append ? null : new String[] {
                "Version", "File Name", "Method Name",
                "LOC", "CognitiveComplexity", "CyclomaticComplexity",
                "CodeSmells", "NestingDepth", "ParameterCount",
                "ChurnTotal", "AvgAdded", "MaxAdded", "AvgDeleted", "MaxDeleted", "AvgChurn", "MaxChurn",
                "ElseAdded", "ElseDeleted", "CondChanges",
                "DecisionPoints",
                "Histories", "Authors",
                "Buggy"
        };

        CSVFormat fmt = append
                ? CSVFormat.DEFAULT
                : CSVFormat.DEFAULT.withHeader(headers);

        try (CSVPrinter csv = new CSVPrinter(new FileWriter(outputCsv, append), fmt)) {
            Set<String> buggySet = info.buggyMethods;

            for (Map.Entry<File, Map<String, FeatureExtractor.MethodFeatures>> fe : featuresPerFile.entrySet()) {
                String fileName = fe.getKey().getName();
                for (Map.Entry<String, FeatureExtractor.MethodFeatures> me : fe.getValue().entrySet()) {
                    String sig = me.getKey();
                    String id  = fileName + "#" + sig;
                    FeatureExtractor.MethodFeatures f = me.getValue();

                    // fetch all metrics, with default fallbacks
                    int   totalChurn = info.churnOfMethod.getOrDefault(id, 0);
                    double avgAdd    = info.avgAddedOfMethod.getOrDefault(id, 0.0);
                    int   maxAdd    = info.maxAddedOfMethod.getOrDefault(id, 0);
                    double avgDel    = info.avgDeletedOfMethod.getOrDefault(id, 0.0);
                    int   maxDel    = info.maxDeletedOfMethod.getOrDefault(id, 0);
                    double avgCh    = info.avgChurnOfMethod.getOrDefault(id, 0.0);
                    int   maxCh    = info.maxChurnOfMethod.getOrDefault(id, 0);
                    int   elseAdd  = info.elseAddedOfMethod.getOrDefault(id, 0);
                    int   elseDel  = info.elseDeletedOfMethod.getOrDefault(id, 0);
                    int   condCh   = info.condChangesOfMethod.getOrDefault(id, 0);
                    int   histories= info.methodHistoriesOfMethod.getOrDefault(id, 0);
                    int   authors  = info.authorsOfMethod.getOrDefault(id, 0);
                    int   decision = f.decisionPoints;
                    boolean isBuggy= buggySet.contains(id);

                    csv.printRecord(
                            version,
                            fileName,
                            sig,
                            f.methodLength,
                            f.cognitiveComplexity,
                            f.cyclomaticComplexity,
                            f.codeSmells,
                            f.nestingDepth,
                            f.parameterCount,
                            totalChurn,
                            String.format("%.2f", avgAdd),
                            maxAdd,
                            String.format("%.2f", avgDel),
                            maxDel,
                            String.format("%.2f", avgCh),
                            maxCh,
                            elseAdd,
                            elseDel,
                            condCh,
                            decision,
                            histories,
                            authors,
                            isBuggy ? "Yes" : "No"
                    );
                }
            }
        }
    }
}
