package fr.emse.ismin.reservation.dtos;

import java.time.Instant;
import java.util.Map;

/**
 * Common body of every error returned by the API ({@code ApiErrorResponse} in the OpenAPI contract).
 *
 * @param code        error code, for example {@code ROOM_NOT_FOUND}
 * @param message     readable description of the error
 * @param timestamp   moment the error occurred
 * @param path        path of the request that failed
 * @param details     extra information about the error, empty by default
 * @param fieldErrors invalid fields with the reason, empty by default
 */
public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path,
        Map<String, Object> details,
        Map<String, String> fieldErrors) {
}
