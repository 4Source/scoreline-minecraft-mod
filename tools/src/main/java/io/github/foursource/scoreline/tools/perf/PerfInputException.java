package io.github.foursource.scoreline.tools.perf;

/** An input file that cannot be read as results; the message names the file. */
public final class PerfInputException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * @param message what is wrong, naming the file
     * @param cause the underlying error, or {@code null}
     */
    public PerfInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
