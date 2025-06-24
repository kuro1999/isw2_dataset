package dataset.creation.features;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Genera un CSV “professore-style” con:
 *   Version,File Name,Method Name,
 *   LOC,CognitiveComplexity,CyclomaticComplexity,
 *   CodeSmells,NestingDepth,ParameterCount,
 *   DecisionPoints,Buggy
 */
public class CsvGenerator {

    private final int version;

    /**
     * @param version numero di versione da scrivere nella colonna "Version"
     */
    public CsvGenerator(int version) {
        this.version = version;
    }

    /**
     * @param featuresPerFile mappa file → (mappa methodId→feature)
     * @param buggyMethods    insieme di methodId considerati buggy
     * @param outputCsv       path del file di output
     */
    public void generateCsv(
            Map<File, Map<String, FeatureExtractor.MethodFeatures>> featuresPerFile,
            Set<String> buggyMethods,
            String outputCsv
    ) throws Exception {
        try (CSVPrinter printer = new CSVPrinter(
                new FileWriter(outputCsv),
                CSVFormat.DEFAULT
                        .withHeader(
                                "Version",
                                "File Name",
                                "Method Name",
                                "LOC",
                                "CognitiveComplexity",
                                "CyclomaticComplexity",
                                "CodeSmells",
                                "NestingDepth",
                                "ParameterCount",
                                "DecisionPoints",
                                "Buggy"
                        )
        )) {
            for (Map.Entry<File, Map<String, FeatureExtractor.MethodFeatures>> entry : featuresPerFile.entrySet()) {
                String fileName = entry.getKey().getName();
                for (Map.Entry<String, FeatureExtractor.MethodFeatures> me : entry.getValue().entrySet()) {
                    String methodId = me.getKey(); // es. "void foo(int)"
                    FeatureExtractor.MethodFeatures f = me.getValue();

                    // split methodId in Method Name
                    String methodName = methodId;

                    // label come Yes/No
                    String buggy = buggyMethods.contains(fileName + "#" + methodId) ? "Yes" : "No";

                    printer.printRecord(
                            version,
                            fileName,
                            methodName,
                            f.methodLength,
                            f.cognitiveComplexity,
                            f.cyclomaticComplexity,
                            f.codeSmells,
                            f.nestingDepth,
                            f.parameterCount,
                            f.decisionPoints,
                            buggy
                    );
                }
            }
        }
    }
}
