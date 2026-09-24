package fr.emse.ismin.reservation.models;

/**
 * Status of a reservation. A {@link #CANCELLED} reservation stays visible
 * but no longer blocks the room.
 */
public enum ReservationStatus {
    CONFIRMED,
    CANCELLED
}
