package fr.emse.ismin.reservation.repositories;

import fr.emse.ismin.reservation.models.Organizer;
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
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Returns all organizers sorted by name (case-insensitive), then by id.
     *
     * @return the sorted organizers
     */
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
