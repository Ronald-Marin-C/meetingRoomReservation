package fr.emse.ismin.reservation.exceptions;

import lombok.Getter;

import java.util.Map;

/**
 * Base class of the business exceptions of the API. It carries everything
 * needed to build an {@code ApiErrorResponse}: the error code (which gives the
 * HTTP status), a readable message, and optional details and field errors.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;
    private final Map<String, String> fieldErrors;

    /**
     * @param code        the API error code
     * @param message     the message returned to the client
     * @param details     extra information about the error, for example the id of the missing resource
     * @param fieldErrors invalid fields with the reason, empty when the error is not about a field
     */
    protected ApiException(ErrorCode code, String message,
                           Map<String, Object> details, Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.details = Map.copyOf(details);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }
}
