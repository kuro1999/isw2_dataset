package dataset.creation.exceptions;

/**
 * Eccezione sollevata in caso di errori durante l'inizializzazione o
 * l'iniezione dei commit dal repository Git.
 */
public class GitInjectionException extends Exception {
    public GitInjectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
