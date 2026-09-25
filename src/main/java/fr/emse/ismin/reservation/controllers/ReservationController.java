package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.dtos.AutomaticReservationRequest;
import fr.emse.ismin.reservation.dtos.CreateReservationRequest;
import fr.emse.ismin.reservation.dtos.ReservationResponse;
import fr.emse.ismin.reservation.models.Reservation;
import fr.emse.ismin.reservation.services.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HTTP endpoints of the reservations ({@code /api/reservations}).
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * Books the room chosen by the client.
     *
     * @param request the reservation to create
     * @return 201 with the confirmed reservation and its URI in the {@code Location} header
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        Reservation reservation = reservationService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{reservationId}")
                .buildAndExpand(reservation.getId())
                .toUri();
        return ResponseEntity.created(location).body(ReservationResponse.from(reservation));
    }

    /**
     * Books the most suitable room, chosen by the application.
     *
     * @param request the reservation to create, without room
     * @return 201 with the confirmed reservation and its URI in the {@code Location} header
     */
    @PostMapping("/automatic")
    public ResponseEntity<ReservationResponse> createAutomatic(
            @Valid @RequestBody AutomaticReservationRequest request) {
        Reservation reservation = reservationService.createAutomatic(request);
        // The new reservation lives under /api/reservations, not under /api/reservations/automatic
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/reservations/{reservationId}")
                .buildAndExpand(reservation.getId())
                .toUri();
        return ResponseEntity.created(location).body(ReservationResponse.from(reservation));
    }

    /**
     * Lists the reservations, confirmed and cancelled, matching every given filter.
     *
     * @param roomId      keep only the reservations of this room
     * @param organizerId keep only the reservations of this organizer
     * @param from        keep only the reservations ending strictly after this date
     * @param to          keep only the reservations starting strictly before this date
     * @return the matching reservations, sorted by start then id
     */
    @GetMapping
    public List<ReservationResponse> findAll(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long organizerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return reservationService.findAll(roomId, organizerId, toInstant(from), toInstant(to)).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    /**
     * @param reservationId id of the reservation
     * @return the reservation, confirmed or cancelled
     */
    @GetMapping("/{reservationId}")
    public ReservationResponse findById(@PathVariable Long reservationId) {
        return ReservationResponse.from(reservationService.findById(reservationId));
    }

    /**
     * Cancels a confirmed reservation.
     *
     * @param reservationId id of the reservation
     * @return the cancelled reservation
     */
    @PatchMapping("/{reservationId}/cancel")
    public ReservationResponse cancel(@PathVariable Long reservationId) {
        return ReservationResponse.from(reservationService.cancel(reservationId));
    }

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }
}
