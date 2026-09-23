package fr.emse.ismin.reservation.models;

/**
 * Status of a room. A room in {@link #MAINTENANCE} can be neither proposed nor booked.
 */
public enum RoomStatus {
    AVAILABLE,
    MAINTENANCE
}
