package dataset.creation.features.csv;

import dataset.creation.exceptions.CsvGeneratorException;
import dataset.creation.features.BuggyInfo;
import dataset.creation.features.MethodFeatures;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    ) throws CsvGeneratorException {

        CSVFormat fmt = getCsvFormat();

        try (CSVPrinter csv = new CSVPrinter(new FileWriter(outputCsv, append), fmt)) {
            // Prepara insieme dei buggy-id
            Set<String> normalizedBuggy = info.getBuggyMethods().stream()
                    .map(CsvGenerator::normalizeId)
                    .collect(Collectors.toSet());

            // Solo due livelli di annidamento e poi chiamo il helper
            for (Map.Entry<File, Map<String, MethodFeatures>> fe : featuresPerFile.entrySet()) {
                String fileName = fe.getKey().getName();
                for (Map.Entry<String, MethodFeatures> me : fe.getValue().entrySet()) {
                    String signature = me.getKey();
                    MethodFeatures f = me.getValue();

                    String rawId = fileName + "#" + signature;
                    String normId = normalizeId(rawId);
                    MethodMetrics mm = info.getMetricsFor(normId);
                    boolean isBuggy = normalizedBuggy.contains(normId);

                    // delego tutta la formattazione/stampa a un helper
                    printCsvLine(csv, version, fileName, signature, f, mm, isBuggy);
                }
            }
        } catch (IOException e) {
            throw new CsvGeneratorException("Errore generazione CSV in " + outputCsv, e);
        }
    }

    private CSVFormat getCsvFormat() {
        String[] headers = append ? null : new String[]{
                "Version","File Name","Method Name",
                "LOC","CognitiveComplexity","CyclomaticComplexity",
                "CodeSmells","NestingDepth","ParameterCount",
                "ChurnTotal","AvgAdded","MaxAdded","AvgDeleted","MaxDeleted",
                "AvgChurn","MaxChurn","ElseAdded","ElseDeleted","CondChanges",
                "DecisionPoints","Histories","Authors","Buggy"
        };
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder();
        if (!append) builder.setHeader(headers);
        return builder.build();
    }

    /**
     * Helper che estrae i metric–default e stampa la singola riga.
     */
    private void printCsvLine(
            CSVPrinter csv,
            String version,
            String fileName,
            String signature,
            MethodFeatures f,
            MethodMetrics mm,
            boolean isBuggy
    ) throws IOException {
        // se mm è null, uso metriche a zero
        StructuralChangeMetrics structural = (mm != null)
                ? mm.getStructural()
                : new StructuralChangeMetrics(0, 0.0, 0, 0);
        AddDeleteMetrics addDel = (mm != null)
                ? mm.getAddDelete()
                : new AddDeleteMetrics(0.0, 0, 0.0, 0);
        ElseMetrics elseM = (mm != null)
                ? mm.getElseMetrics()
                : new ElseMetrics(0, 0);
        ComplexityMetrics comp = (mm != null)
                ? mm.getComplexity()
                : new ComplexityMetrics(0, 0);

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

                structural.getChurn(),
                String.format("%.2f", addDel.getAvgAdded()),
                addDel.getMaxAdded(),
                String.format("%.2f", addDel.getAvgDeleted()),
                addDel.getMaxDeleted(),

                String.format("%.2f", structural.getAvgChurn()),
                structural.getMaxChurn(),

                elseM.getElseAdded(),
                elseM.getElseDeleted(),
                structural.getCondChanges(),

                f.getDecisionPoints(),
                comp.getHistoryCount(),
                comp.getAuthorCount(),

                isBuggy ? "Yes" : "No"
        );
    }

}
