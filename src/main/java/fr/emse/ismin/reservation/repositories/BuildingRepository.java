package fr.emse.ismin.reservation.repositories;

import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.TextNormalizer;
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
    default boolean existsByNameIgnoreCase(String name) {
        return existsByNormalizedName(TextNormalizer.normalize(name));
    }

    /**
     * Checks whether another building than the given one already uses this name, ignoring case.
     * Used when renaming a building.
     *
     * @param name the name to look for
     * @param id   the id of the building being updated
     * @return {@code true} if the name is taken by another building
     */
    default boolean existsByNameIgnoreCaseAndIdNot(String name, Long id) {
        return existsByNormalizedNameAndIdNot(TextNormalizer.normalize(name), id);
    }

    /**
     * @param normalizedName name key built with {@link TextNormalizer#normalize(String)}
     * @return {@code true} if a building has this key
     */
    boolean existsByNormalizedName(String normalizedName);

    /**
     * @param normalizedName name key built with {@link TextNormalizer#normalize(String)}
     * @param id             id of the building to ignore
     * @return {@code true} if another building has this key
     */
    boolean existsByNormalizedNameAndIdNot(String normalizedName, Long id);

    /**
     * Returns all buildings sorted by name (case-insensitive), then by id.
     *
     * @return the sorted buildings
     */
    @Query("SELECT b FROM Building b ORDER BY b.normalizedName, b.id")
    List<Building> findAllSortedByName();
}
