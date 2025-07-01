package dataset.creation.features;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

import dataset.creation.features.metrics.AddDeleteMetrics;
import dataset.creation.features.metrics.ComplexityMetrics;
import dataset.creation.features.metrics.ElseMetrics;
import dataset.creation.features.metrics.MethodMetrics;
import dataset.creation.features.metrics.StructuralChangeMetrics;

/**
 * Genera CSV includendo:
 *  - static metrics (LOC, complexity, code smells, ecc.)
 *  - churn metrics (total, avg, max)
 *  - change details (elseAdded, elseDeleted, condChanges)
 *  - history details (n° commit, n° autori)
 *  - label buggy (normalizzato per whitespace)
 */
public class CsvGenerator {

    private final String version;
    private final boolean append;

    public CsvGenerator(String version, boolean append) {
        this.version = version;
        this.append = append;
    }

    /** Rimuove tutti gli spazi per creare ID uniformi */
    private static String normalizeId(String rawId) {
        return rawId.replaceAll("\\s+", "");
    }

    public void generateCsv(
            Map<File, Map<String, MethodFeatures>> featuresPerFile,
            BuggyInfo info,
            String outputCsv
    ) throws Exception {

        // Header extended con le colonne nuove
        String[] headers = append ? null : new String[]{
                "Version", "File Name", "Method Name",
                "LOC", "CognitiveComplexity", "CyclomaticComplexity",
                "CodeSmells", "NestingDepth", "ParameterCount",
                "ChurnTotal", "AvgAdded", "MaxAdded", "AvgDeleted", "MaxDeleted", "AvgChurn", "MaxChurn",
                "ElseAdded", "ElseDeleted", "CondChanges",
                "DecisionPoints",
                "Histories", "Authors",
                "Buggy"
        };

        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder();
        if (!append) builder.setHeader(headers);
        CSVFormat fmt = builder.build();

        try (CSVPrinter csv = new CSVPrinter(new FileWriter(outputCsv, append), fmt)) {

            // Prepara set normalizzato per il label buggy
            Set<String> normalizedBuggy = info.getBuggyMethods().stream()
                    .map(CsvGenerator::normalizeId)
                    .collect(Collectors.toSet());

            for (Map.Entry<File, Map<String, MethodFeatures>> fe : featuresPerFile.entrySet()) {
                String fileName = fe.getKey().getName();

                for (Map.Entry<String, MethodFeatures> me : fe.getValue().entrySet()) {
                    String signature = me.getKey();
                    String rawId     = fileName + "#" + signature;
                    String normId    = normalizeId(rawId);

                    MethodFeatures f = me.getValue();

                    // Estrai MethodMetrics (o crea uno "vuoto" a zero)
                    MethodMetrics mm = info.getMetricsFor(normId);
                    StructuralChangeMetrics structural = mm != null
                            ? mm.getStructural()
                            : new StructuralChangeMetrics(0, 0.0, 0, 0);
                    AddDeleteMetrics addDel = mm != null
                            ? mm.getAddDelete()
                            : new AddDeleteMetrics(0.0, 0, 0.0, 0);
                    ElseMetrics elseM = mm != null
                            ? mm.getElseMetrics()
                            : new ElseMetrics(0, 0);
                    ComplexityMetrics comp = mm != null
                            ? mm.getComplexity()
                            : new ComplexityMetrics(0, 0);

                    // Ricava i valori
                    int totalChurn   = structural.getChurn();
                    double avgAdd    = addDel.getAvgAdded();
                    int maxAdd       = addDel.getMaxAdded();
                    double avgDel    = addDel.getAvgDeleted();
                    int maxDel       = addDel.getMaxDeleted();
                    double avgCh     = structural.getAvgChurn();
                    int maxCh        = structural.getMaxChurn();
                    int elseAdd      = elseM.getElseAdded();
                    int elseDel      = elseM.getElseDeleted();
                    int condCh       = structural.getCondChanges();
                    int histories    = comp.getHistoryCount();
                    int authors      = comp.getAuthorCount();
                    int decisionPts  = f.getDecisionPoints();

                    boolean isBuggy = normalizedBuggy.contains(normId);

                    csv.printRecord(
                            version,
                            fileName,
                            signature,
                            f.getMethodLength(),
                            f.getCognitiveComplexity(),
                            f.getCyclomaticComplexity(),
                            f.getCodeSmells(),
                            f.getNestingDepth(),
                            f.getParameterCount(),
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
                            decisionPts,
                            histories,
                            authors,
                            isBuggy ? "Yes" : "No"
                    );
                }
            }
        }
    }
}
