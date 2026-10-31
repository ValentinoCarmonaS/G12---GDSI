package com.easyeat.dish.exception;

/**
 * The rejection feedback does not satisfy FR-021: it is absent, blank, or outside 10 to 1000
 * characters measured <b>after</b> trimming.
 *
 * <p>Carries the trimmed length rather than the raw text. The length is what the Administrator
 * needs in order to fix the input, and echoing the comment back into an error payload would put
 * free text the Chef never approved into a second surface for no benefit.
 *
 * <p>Thrown from the domain for the same reason as {@link InvalidRetailPriceException}: FR-021
 * states 400, and bean validation in this codebase yields 422.
 *
 * <p>Maps to 400 {@code invalid-rejection-comment}.
 */
public class InvalidRejectionCommentException extends RuntimeException {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 1000;

    private final Integer trimmedLength;

    public InvalidRejectionCommentException(String rawComment) {
        super(buildMessage(rawComment));
        this.trimmedLength = rawComment == null ? null : rawComment.trim().length();
    }

    private static String buildMessage(String rawComment) {
        if (rawComment == null || rawComment.trim().isEmpty()) {
            return String.format(
                    "El rechazo requiere un comentario de entre %d y %d caracteres. No se recibió "
                            + "ninguno, o solo espacios en blanco.",
                    MIN_LENGTH, MAX_LENGTH);
        }
        return String.format(
                "El comentario de rechazo debe tener entre %d y %d caracteres sin contar espacios "
                        + "al inicio ni al final. Se recibieron %d.",
                MIN_LENGTH, MAX_LENGTH, rawComment.trim().length());
    }

    /** {@code null} when no comment arrived at all, which is a different fault from a short one. */
    public Integer getTrimmedLength() {
        return trimmedLength;
    }
}
