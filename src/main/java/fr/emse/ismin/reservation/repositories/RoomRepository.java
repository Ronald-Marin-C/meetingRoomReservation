package fr.emse.ismin.reservation.repositories;

import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import fr.emse.ismin.reservation.models.TextNormalizer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Room} entities.
 * <p>
 * List queries load the building and the equipment of each room in the same
 * query, since both are always part of the API responses.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Checks whether a room already uses this name, ignoring case.
     *
     * @param name the name to look for
     * @return {@code true} if the name is already taken
     */
    default boolean existsByNameIgnoreCase(String name) {
        return existsByNormalizedName(TextNormalizer.normalize(name));
    }

    /**
     * Checks whether another room than the given one already uses this name, ignoring case.
     * Used when renaming a room.
     *
     * @param name the name to look for
     * @param id   the id of the room being updated
     * @return {@code true} if the name is taken by another room
     */
    default boolean existsByNameIgnoreCaseAndIdNot(String name, Long id) {
        return existsByNormalizedNameAndIdNot(TextNormalizer.normalize(name), id);
    }

    /**
     * @param normalizedName name key built with {@link TextNormalizer#normalize(String)}
     * @return {@code true} if a room has this key
     */
    boolean existsByNormalizedName(String normalizedName);

    /**
     * @param normalizedName name key built with {@link TextNormalizer#normalize(String)}
     * @param id             id of the room to ignore
     * @return {@code true} if another room has this key
     */
    boolean existsByNormalizedNameAndIdNot(String normalizedName, Long id);

    /**
     * Returns a room with its building and equipment loaded, since both are
     * always part of the API responses.
     *
     * @param id the room id
     * @return the room, or empty if it does not exist
     */
    @Override
    @EntityGraph(attributePaths = {"building", "equipment"})
    Optional<Room> findById(Long id);

    /**
     * Returns all rooms sorted by name (case-insensitive), then by id.
     *
     * @return the sorted rooms
     */
    @EntityGraph(attributePaths = {"building", "equipment"})
    @Query("SELECT r FROM Room r ORDER BY r.normalizedName, r.id")
    List<Room> findAllSortedByName();

    /**
     * Returns the rooms having the given status, for example the rooms that can
     * be proposed for a reservation.
     *
     * @param status the expected status
     * @return the matching rooms
     */
    @EntityGraph(attributePaths = {"building", "equipment"})
    List<Room> findByStatus(RoomStatus status);

    /**
     * Returns the highest floor occupied by a room in a building. Used to refuse
     * reducing the number of floors below an occupied floor.
     *
     * @param buildingId the building id
     * @return the highest occupied floor, or empty if the building has no room
     */
    @Query("SELECT MAX(r.floor) FROM Room r WHERE r.building.id = :buildingId")
    Optional<Integer> findHighestFloorInBuilding(@Param("buildingId") Long buildingId);
}
