package fr.emse.ismin.reservation.repositories;

import fr.emse.ismin.reservation.models.Reservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Data access for {@link Reservation} entities.
 * <p>
 * Two periods overlap when {@code existing.start < requested.end} and
 * {@code existing.end > requested.start}. Consecutive periods (for example
 * 10:00-11:00 and 11:00-12:00) therefore do not overlap. Cancelled reservations
 * never block a room.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Returns a reservation with everything its API response needs: room,
     * organizer, their buildings and the requested equipment.
     *
     * @param id the reservation id
     * @return the reservation, or empty if it does not exist
     */
    @Override
    @EntityGraph(attributePaths = {"room.building", "organizer.building", "requiredEquipment"})
    Optional<Reservation> findById(Long id);

    /**
     * Returns the confirmed reservations of a room overlapping the given period,
     * sorted by start date then id.
     *
     * @param roomId the room id
     * @param start  start of the requested period (inclusive)
     * @param end    end of the requested period (exclusive)
     * @return the conflicting reservations, empty if the room is free
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.room.id = :roomId
              AND r.status = fr.emse.ismin.reservation.models.ReservationStatus.CONFIRMED
              AND r.start < :end
              AND r.end > :start
            ORDER BY r.start, r.id
            """)
    List<Reservation> findConflictingReservations(@Param("roomId") Long roomId,
                                                  @Param("start") Instant start,
                                                  @Param("end") Instant end);

    /**
     * Returns the confirmed reservations of every room overlapping the given
     * period, with their room. Used by the automatic assignment, which checks
     * the overlaps itself.
     *
     * @param start start of the requested period (inclusive)
     * @param end   end of the requested period (exclusive)
     * @return the overlapping confirmed reservations
     */
    @Query("""
            SELECT r FROM Reservation r JOIN FETCH r.room
            WHERE r.status = fr.emse.ismin.reservation.models.ReservationStatus.CONFIRMED
              AND r.start < :end
              AND r.end > :start
            """)
    List<Reservation> findConfirmedOverlapping(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Returns the ids of every room having at least one confirmed reservation
     * overlapping the given period. Used to exclude busy rooms in a single query
     * when searching for available rooms.
     *
     * @param start start of the requested period (inclusive)
     * @param end   end of the requested period (exclusive)
     * @return the ids of the busy rooms
     */
    @Query("""
            SELECT DISTINCT r.room.id FROM Reservation r
            WHERE r.status = fr.emse.ismin.reservation.models.ReservationStatus.CONFIRMED
              AND r.start < :end
              AND r.end > :start
            """)
    Set<Long> findBusyRoomIds(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Returns the reservations (confirmed and cancelled) matching all the given
     * filters, sorted by start date then id. A {@code null} filter is ignored.
     * With {@code from} and {@code to}, the reservations overlapping that period are kept.
     *
     * @param roomId      keep only the reservations of this room
     * @param organizerId keep only the reservations of this organizer
     * @param from        keep only the reservations ending strictly after this date
     * @param to          keep only the reservations starting strictly before this date
     * @return the matching reservations
     */
    @EntityGraph(attributePaths = {"room.building", "organizer.building", "requiredEquipment"})
    @Query("""
            SELECT r FROM Reservation r
            WHERE (:roomId IS NULL OR r.room.id = :roomId)
              AND (:organizerId IS NULL OR r.organizer.id = :organizerId)
              AND (CAST(:from AS Instant) IS NULL OR r.end > :from)
              AND (CAST(:to AS Instant) IS NULL OR r.start < :to)
            ORDER BY r.start, r.id
            """)
    List<Reservation> findWithFilters(@Param("roomId") Long roomId,
                                      @Param("organizerId") Long organizerId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);
}
