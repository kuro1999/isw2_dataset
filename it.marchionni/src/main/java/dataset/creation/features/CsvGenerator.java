package dataset.creation.features;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.Set;

/**
 * Genera il CSV finale con tutte le metriche richieste.
 * Colonne:
 *   Version,File Name,Method Name,
 *   LOC,CognitiveComplexity,CyclomaticComplexity,
 *   CodeSmells,NestingDepth,ParameterCount,DecisionPoints,
 *   Priority,Buggy
 */
public class CsvGenerator {

    private final int version;
    public CsvGenerator(int version) { this.version = version; }

    /** @param featuresPerFile  file → (methodId → feature)
     *  @param info              insieme metodi buggy + priority per metodo
     *  @param outputCsv         path del CSV in uscita */
    public void generateCsv(
            Map<File, Map<String, FeatureExtractor.MethodFeatures>> featuresPerFile,
            BuggyMethodExtractor.BuggyInfo info,
            String outputCsv) throws Exception {

        try (CSVPrinter csv = new CSVPrinter(
                new FileWriter(outputCsv),
                CSVFormat.DEFAULT.withHeader(
                        "Version","File Name","Method Name",
                        "LOC","CognitiveComplexity","CyclomaticComplexity",
                        "CodeSmells","NestingDepth","ParameterCount","DecisionPoints","Buggy"))) {

            Set<String> buggy = info.buggyMethods;

            for (Map.Entry<File, Map<String, FeatureExtractor.MethodFeatures>> e : featuresPerFile.entrySet()) {
                String fileName = e.getKey().getName();

                for (Map.Entry<String, FeatureExtractor.MethodFeatures> m : e.getValue().entrySet()) {
                    String methodSig = m.getKey();                         // es. "void foo(int)"
                    String methodId   = fileName + "#" + methodSig;        // chiave globale

                    FeatureExtractor.MethodFeatures f = m.getValue();
                    boolean isBuggy = buggy.contains(methodId);

                    csv.printRecord(
                            version,
                            fileName,
                            methodSig,
                            f.methodLength,
                            f.cognitiveComplexity,
                            f.cyclomaticComplexity,
                            f.codeSmells,
                            f.nestingDepth,
                            f.parameterCount,
                            f.decisionPoints,
                            isBuggy ? "Yes" : "No"
                    );
                }
            }
        }
    }
}
