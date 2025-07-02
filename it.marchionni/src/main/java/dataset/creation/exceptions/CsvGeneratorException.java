package dataset.creation.exceptions;

/**
 * Eccezione sollevata in caso di errori nella generazione del CSV.
 */
public class CsvGeneratorException extends Exception {
    public CsvGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
