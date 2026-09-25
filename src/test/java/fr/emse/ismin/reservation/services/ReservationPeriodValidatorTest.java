package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.InvalidReservationPeriodException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests of {@link ReservationPeriodValidator}, with a clock fixed on
 * 2026-10-01 at 10:00 UTC.
 */
class ReservationPeriodValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private ReservationPeriodValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ReservationPeriodValidator(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void testValidateFuturePeriodOfTwoHours() {
        // GIVEN a period of two hours tomorrow
        Instant start = Instant.parse("2026-10-02T14:00:00Z");
        Instant end = Instant.parse("2026-10-02T16:00:00Z");

        // WHEN validating it
        // THEN it is accepted
        assertDoesNotThrow(() -> validator.validate(start, end));
    }

    @Test
    void testValidatePeriodStartingNow() {
        // GIVEN a period starting exactly now
        // WHEN validating it
        // THEN it is accepted, since the start is not in the past
        assertDoesNotThrow(() -> validator.validate(NOW, NOW.plusSeconds(3600)));
    }

    @Test
    void testValidatePeriodEndingBeforeItStarts() {
        // GIVEN a period whose end is before its start
        Instant start = Instant.parse("2026-10-02T16:00:00Z");
        Instant end = Instant.parse("2026-10-02T14:00:00Z");

        // WHEN validating it
        InvalidReservationPeriodException exception = assertThrows(InvalidReservationPeriodException.class,
                () -> validator.validate(start, end));

        // THEN it is refused because the order is wrong
        assertEquals(ErrorCode.INVALID_RESERVATION_PERIOD, exception.getCode());
        assertEquals("La date de début doit être antérieure à la date de fin", exception.getMessage());
    }

    @Test
    void testValidateEmptyPeriod() {
        // GIVEN a period starting and ending at the same time
        Instant start = Instant.parse("2026-10-02T14:00:00Z");

        // WHEN validating it
        // THEN it is refused, since the start must be strictly before the end
        assertThrows(InvalidReservationPeriodException.class, () -> validator.validate(start, start));
    }

    @Test
    void testValidatePeriodStartingInThePast() {
        // GIVEN a period that started one minute ago
        Instant start = NOW.minusSeconds(60);

        // WHEN validating it
        InvalidReservationPeriodException exception = assertThrows(InvalidReservationPeriodException.class,
                () -> validator.validate(start, NOW.plusSeconds(3600)));

        // THEN it is refused because it is in the past
        assertEquals("La date de début ne peut pas être dans le passé", exception.getMessage());
    }

    @Test
    void testValidatePeriodOfExactlyEightHours() {
        // GIVEN a period of exactly eight hours
        Instant start = Instant.parse("2026-10-02T08:00:00Z");
        Instant end = Instant.parse("2026-10-02T16:00:00Z");

        // WHEN validating it
        // THEN it is accepted
        assertDoesNotThrow(() -> validator.validate(start, end));
    }

    @Test
    void testValidatePeriodLongerThanEightHours() {
        // GIVEN a period of eight hours and one minute
        Instant start = Instant.parse("2026-10-02T08:00:00Z");
        Instant end = Instant.parse("2026-10-02T16:01:00Z");

        // WHEN validating it
        InvalidReservationPeriodException exception = assertThrows(InvalidReservationPeriodException.class,
                () -> validator.validate(start, end));

        // THEN it is refused because it is too long
        assertEquals("Une réservation ne peut pas dépasser huit heures", exception.getMessage());
    }

    @Test
    void testValidatePeriodSentWithDifferentOffsets() {
        // GIVEN a start in Paris time and an end in UTC, one hour apart
        Instant start = OffsetDateTime.parse("2026-10-02T14:00:00+02:00").toInstant();
        Instant end = OffsetDateTime.parse("2026-10-02T13:00:00Z").toInstant();

        // WHEN validating it
        // THEN it is accepted, since both dates are compared on the same timeline
        assertDoesNotThrow(() -> validator.validate(start, end));
    }
}
