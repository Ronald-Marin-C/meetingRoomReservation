package fr.emse.ismin.reservation.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Thrown when a request is valid but conflicts with the current state of the
 * data (HTTP 409), for example a room already booked or a name already used.
 */
public class ConflictException extends ApiException {

    /**
     * @param code    a conflict error code, such as {@code ROOM_ALREADY_RESERVED}
     * @param message the message returned to the client
     * @param details extra information, for example the id of the conflicting reservation
     * @throws IllegalArgumentException if the code is not a conflict code
     */
    public ConflictException(ErrorCode code, String message, Map<String, Object> details) {
        super(requireConflictCode(code), message, details, Map.of());
    }

    /**
     * @param code    a conflict error code, such as {@code RESOURCE_ALREADY_EXISTS}
     * @param message the message returned to the client
     */
    public ConflictException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    private static ErrorCode requireConflictCode(ErrorCode code) {
        if (code.getStatus() != HttpStatus.CONFLICT) {
            throw new IllegalArgumentException(code + " is not a conflict error code");
        }
        return code;
    }
}
