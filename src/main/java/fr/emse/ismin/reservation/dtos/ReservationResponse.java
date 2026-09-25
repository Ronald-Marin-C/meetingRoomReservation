package fr.emse.ismin.reservation.dtos;

import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Reservation;
import fr.emse.ismin.reservation.models.ReservationStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Reservation as returned by the API ({@code ReservationResponse} in the OpenAPI contract).
 * Dates are returned in UTC.
 *
 * @param id                     reservation id
 * @param title                  title of the meeting
 * @param status                 {@code CONFIRMED} or {@code CANCELLED}
 * @param room                   booked room, without its equipment
 * @param organizer              organizer, without the email address
 * @param start                  start of the period
 * @param end                    end of the period
 * @param numberOfParticipants   number of people attending
 * @param requiredEquipmentCodes codes of the requested equipment, sorted
 * @param createdAt              moment the reservation was created
 */
public record ReservationResponse(Long id, String title, ReservationStatus status, RoomSummaryResponse room,
                                  OrganizerSummaryResponse organizer, OffsetDateTime start, OffsetDateTime end,
                                  Integer numberOfParticipants, List<String> requiredEquipmentCodes,
                                  OffsetDateTime createdAt) {

    /**
     * Builds the response from the entity.
     *
     * @param reservation the reservation entity, with its room, organizer and equipment loaded
     * @return the matching response
     */
    public static ReservationResponse from(Reservation reservation) {
        List<String> equipmentCodes = reservation.getRequiredEquipment().stream()
                .map(Equipment::getCode)
                .sorted()
                .toList();
        return new ReservationResponse(reservation.getId(), reservation.getTitle(), reservation.getStatus(),
                RoomSummaryResponse.from(reservation.getRoom()),
                OrganizerSummaryResponse.from(reservation.getOrganizer()),
                inUtc(reservation.getStart()), inUtc(reservation.getEnd()),
                reservation.getNumberOfParticipants(), equipmentCodes, inUtc(reservation.getCreatedAt()));
    }

    private static OffsetDateTime inUtc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
