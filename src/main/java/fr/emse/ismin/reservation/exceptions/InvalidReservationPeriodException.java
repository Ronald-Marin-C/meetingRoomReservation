package fr.emse.ismin.reservation.exceptions;

import java.util.Map;

/**
 * Thrown when a period is invalid (HTTP 400): start not before end, start in
 * the past, duration over eight hours, or {@code from} not before {@code to}.
 */
public class InvalidReservationPeriodException extends ApiException {

    /**
     * @param message explanation of the rule that is not respected
     */
    public InvalidReservationPeriodException(String message) {
        super(ErrorCode.INVALID_RESERVATION_PERIOD, message, Map.of(), Map.of());
    }
}
