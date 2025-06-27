package dataset.creation.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Dato un CSV che contiene la colonna «Version» + feature del metodo,
 * mantiene UN SOLO record per ogni combinazione di feature identiche
 * (stesso file, stesso nome metodo, stesse metriche, stessa label Buggy)
 * scegliendo la riga con la release più VECCHIA.
 *
 * Uso:
 *   FinalCsvReducer.reduceDuplicates(inputCsv, outputCsv);
 */
public final class FinalCsvReducer {

    private FinalCsvReducer() {/* utility class */}

    /** Esegue la riduzione e scrive il nuovo file. */
    public static void reduceDuplicates(Path input, Path output) throws IOException {
        // 1. Lettura CSV
        CSVFormat inFmt = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .build();

        Map<String,String[]> bestByKey = new LinkedHashMap<>();
        List<String> header;

        try (CSVParser parser = CSVParser.parse(input, StandardCharsets.UTF_8, inFmt)) {
            header = new ArrayList<>(parser.getHeaderNames());
            int versionIdx = header.indexOf("Version");
            if (versionIdx < 0)
                throw new IllegalStateException("Colonna \"Version\" mancante nel CSV");

            for (CSVRecord rec : parser) {
                String version = rec.get(versionIdx);

                /* chiave = tutto tranne Version */
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rec.size(); i++) {
                    if (i == versionIdx) continue;
                    sb.append(rec.get(i)).append('\u0001');   // separatore improbabile
                }
                String key = sb.toString();

                String[] row = rec.toList().toArray(new String[0]);
                String[] stored = bestByKey.get(key);
                if (stored == null || compareSemver(version, stored[versionIdx]) < 0) {
                    bestByKey.put(key, row);                 // salvo la release più vecchia
                }
            }
        }

        // 2. Scrittura CSV ridotto
        CSVFormat outFmt = CSVFormat.DEFAULT.builder()
                .setHeader(header.toArray(new String[0]))
                .build();

        try (BufferedWriter w = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(w, outFmt)) {
            for (String[] row : bestByKey.values()) {
                printer.printRecord((Object[]) row);
            }
        }
    }

    /* --------------------------------------------------------------------- */
    /* ▸ Ausiliari: normalizzazione e confronto semver                       */
    /* --------------------------------------------------------------------- */

    private static String normalize(String tag) {
        return tag.replaceFirst("^(?:v|release-)", "");
    }
    /** -1 se a<b, 0 se uguale, +1 se a>b  */
    private static int compareSemver(String a, String b) {
        String[] pa = normalize(a).split("\\.");
        String[] pb = normalize(b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int ai = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int bi = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }
    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return 0; }
    }
}
