package dataset.creation.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Raccolta di utility per la post-elaborazione dei CSV prodotti dal dataset
 * creator.
 * <ul>
 *   <li><b>deduplicate</b> – rimuove le righe completamente identiche
 *       (stesso valore in <em>tutte</em> le colonne), mantenendo la prima
 *       occorrenza per preservare l’ordinamento originale.</li>
 *   <li><b>dedupAndFilterUpTo</b> – esegue la deduplicazione e, in più,
 *       scarta ogni record con la colonna <code>Version</code> che indica
 *       una release successiva a quella specificata (confronto semantico,
 *       prefissi «v»/«release-» ignorati). Utile, ad esempio, per fermarsi
 *       alla 4.2.1.</li>
 * </ul>
 * <p>La classe è pensata per essere richiamata direttamente dal codice Java
 * (o da uno script build/CI); non contiene più un <code>main</code> perché la
 * logica CLI è stata spostata altrove.</p>
 * <p><b>Compatibile con Java 11.</b></p>
 */
public class CsvDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(CsvDeduplicator.class);

    /* ------------------------------------------------------------------ */
    /* 1. Solo deduplicazione                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Deduplica il file CSV mantenendo la prima occorrenza di ogni riga
     * identica.
     *
     * @param input  percorso del CSV sorgente
     * @param output percorso del CSV di destinazione (sovrascritto se esiste)
     * @throws IOException problemi di I/O lettura/scrittura
     */
    public static void deduplicate(Path input, Path output) throws IOException {
        transform(input, output, rec -> true);
    }


    /* ------------------------------------------------------------------ */
    /* 2. Deduplicazione + filtro release                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Deduplica e filtra tutte le righe che hanno la colonna "Version" con una
     * versione semantica successiva a {@code stopRelease}. La riga viene tenuta
     * se <code>version &lt;= stopRelease</code>.
     *
     * @param input       CSV sorgente
     * @param output      CSV di destinazione (sovrascritto se esiste)
     * @param stopRelease ultima release da mantenere (es. "4.2.1")
     * @throws IOException problemi di I/O
     */
    public static void dedupAndFilterUpTo(Path input, Path output, final String stopRelease) throws IOException {
        log.info("🚧 Filtro fino alla release {} (inclusa)", stopRelease);
        transform(input, output, rec -> compareSemver(rec.get("Version"), stopRelease) <= 0);
    }


    /* ------------------------------------------------------------------ */
    /* 3. Trasformazione comune                                           */
    /* ------------------------------------------------------------------ */

    private interface RowPredicate {
        boolean test(CSVRecord rec);
    }

    private static void transform(Path input, Path output, RowPredicate keep) throws IOException {
        log.info("✍️  Elaboro {} → {}", input.toAbsolutePath(), output.toAbsolutePath());

        // 1) Configuro il formato per il parser usando il nuovo builder (removendo i metodi deprecati)
        CSVFormat parserFormat = CSVFormat.DEFAULT.builder()
                .setHeader()               // legge la prima riga come header
                .setSkipHeaderRecord(true) // non include l’header nei record
                .setTrim(true)             // rimuove spazi ad inizio/fine campo
                .build();

        try (Reader reader = Files.newBufferedReader(input);
             CSVParser parser = CSVParser.parse(reader, parserFormat)) {

            List<String> header = new ArrayList<>(parser.getHeaderMap().keySet());
            Set<String> seen   = new LinkedHashSet<>(); // mantiene l’ordine di inserimento

            // 2) Configuro il formato per il printer usando il builder
            CSVFormat printerFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(header.toArray(new String[0])) // imposta header manuale
                    .build();

            try (Writer writer = Files.newBufferedWriter(output);
                 CSVPrinter printer = new CSVPrinter(writer, printerFormat)) {

                for (CSVRecord rec : parser) {
                    if (!keep.test(rec)) {
                        continue;              // filtro versione
                    }
                    String key = buildKey(rec);
                    if (seen.add(key)) {       // deduplica
                        printer.printRecord(rec);
                    }
                }
            }

            long duplicates = parser.getRecordNumber() - seen.size();
            log.info("✅ Completato: righe finali {} (duplicate rimosse: {})", seen.size(), duplicates);
        }
    }


    /* ------------------------------------------------------------------ */
    /* 4. Helpers                                                         */
    /* ------------------------------------------------------------------ */

    // Concatena tutti i campi con un separatore "\u001F" (unit separator) che non dovrebbe comparire nel CSV.
    private static String buildKey(CSVRecord rec) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rec.size(); i++) {
            if (i > 0) sb.append('\u001F');
            sb.append(rec.get(i));
        }
        return sb.toString();
    }

    /**
     * Confronto semantico a/b: restituisce < 0 se a < b, 0 se uguali, > 0 se a > b.
     * Gestisce anche prefissi "v" o "release-" comuni nei tag.
     */
    public static int compareSemver(String a, String b) {
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
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0; // fallback per eventuali suffissi tipo "-RC1"
        }
    }

    private static String normalize(String v) {
        return v.replaceFirst("^(?:v|release-)", "");
    }
}
