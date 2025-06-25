package dataset.creation.features;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.Set;

public class CsvGenerator {

    private final String  version;
    private final boolean append;

    /**
     * @param version  la versione/tag Git da scrivere in ogni riga
     * @param append   true = non riscrivere l’header, false = scrivere header
     */
    public CsvGenerator(String version, boolean append) {
        this.version = version;
        this.append  = append;
    }

    public void generateCsv(
            Map<File, Map<String, FeatureExtractor.MethodFeatures>> featuresPerFile,
            BuggyMethodExtractor.BuggyInfo info,
            String outputCsv
    ) throws Exception {

        CSVFormat fmt = append
                ? CSVFormat.DEFAULT
                : CSVFormat.DEFAULT.withHeader(
                "Version","File Name","Method Name",
                "LOC","CognitiveComplexity","CyclomaticComplexity",
                "CodeSmells","NestingDepth","ParameterCount",
                "Churn","ElseAdded","ElseDeleted","DecisionPoints","Buggy"
        );

        try (CSVPrinter csv = new CSVPrinter(
                new FileWriter(outputCsv, append), fmt
        )) {
            Set<String> buggy            = info.buggyMethods;
            Map<String,Integer> churn    = info.churnOfMethod;
            Map<String,Integer> eAdded   = info.elseAddedOfMethod;
            Map<String,Integer> eDeleted = info.elseDeletedOfMethod;

            for (var fe : featuresPerFile.entrySet()) {
                String fileName = fe.getKey().getName();
                for (var me : fe.getValue().entrySet()) {
                    String sig = me.getKey();
                    String id  = fileName + "#" + sig;
                    var f      = me.getValue();

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
                            churn.getOrDefault(id, 0),
                            eAdded.getOrDefault(id, 0),
                            eDeleted.getOrDefault(id, 0),
                            f.decisionPoints,
                            buggy.contains(id) ? "Yes" : "No"
                    );
                }
            }
        }
    }
}
