package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.exceptions.InvalidReservationPeriodException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Checks the rules of a reservation period, shared by the availability search
 * and the reservations: the start is strictly before the end, the start is not
 * in the past, and the period lasts at most eight hours.
 */
@Component
@RequiredArgsConstructor
public class ReservationPeriodValidator {

    /** Longest period that can be booked. */
    public static final Duration MAX_DURATION = Duration.ofHours(8);

    private final Clock clock;

    /**
     * @param start start of the period (inclusive)
     * @param end   end of the period (exclusive)
     * @throws InvalidReservationPeriodException if a rule is not respected
     */
    public void validate(Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new InvalidReservationPeriodException("La date de début doit être antérieure à la date de fin");
        }
        if (start.isBefore(Instant.now(clock))) {
            throw new InvalidReservationPeriodException("La date de début ne peut pas être dans le passé");
        }
        if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            throw new InvalidReservationPeriodException("Une réservation ne peut pas dépasser huit heures");
        }
    }
}
