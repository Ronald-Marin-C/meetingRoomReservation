package fr.emse.ismin.reservation.repositories;

import fr.emse.ismin.reservation.models.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access for {@link Building} entities.
 */
@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {

    /**
     * Checks whether a building already uses this name, ignoring case.
     *
     * @param name the name to look for
     * @return {@code true} if the name is already taken
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Checks whether another building than the given one already uses this name, ignoring case.
     * Used when renaming a building.
     *
     * @param name the name to look for
     * @param id   the id of the building being updated
     * @return {@code true} if the name is taken by another building
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Returns all buildings sorted by name (case-insensitive), then by id.
     *
     * @return the sorted buildings
     */
    @Query("SELECT b FROM Building b ORDER BY LOWER(b.name), b.id")
    List<Building> findAllSortedByName();
}
