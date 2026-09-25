package fr.emse.ismin.reservation.repositories;

import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.models.TextNormalizer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Organizer} entities.
 */
@Repository
public interface OrganizerRepository extends JpaRepository<Organizer, Long> {

    /**
     * Checks whether an organizer already uses this email address, ignoring case.
     *
     * @param email the email address to look for
     * @return {@code true} if the email is already taken
     */
    default boolean existsByEmailIgnoreCase(String email) {
        return existsByNormalizedEmail(TextNormalizer.normalize(email));
    }

    /**
     * @param normalizedEmail email key built with {@link TextNormalizer#normalize(String)}
     * @return {@code true} if an organizer has this key
     */
    boolean existsByNormalizedEmail(String normalizedEmail);

    /**
     * Returns all organizers sorted by name (case-insensitive), then by id,
     * with their building loaded in the same query.
     *
     * @return the sorted organizers
     */
    @EntityGraph(attributePaths = "building")
    @Query("SELECT o FROM Organizer o ORDER BY LOWER(o.name), o.id")
    List<Organizer> findAllSortedByName();

    /**
     * Returns the highest floor occupied by an organizer in a building. Used to
     * refuse reducing the number of floors below an occupied floor.
     *
     * @param buildingId the building id
     * @return the highest occupied floor, or empty if no organizer is located in the building
     */
    @Query("SELECT MAX(o.floor) FROM Organizer o WHERE o.building.id = :buildingId")
    Optional<Integer> findHighestFloorInBuilding(@Param("buildingId") Long buildingId);
}
