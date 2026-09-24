package fr.emse.ismin.reservation.repositories;

import fr.emse.ismin.reservation.models.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Data access for {@link Equipment} entities.
 */
@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    /**
     * Checks whether a piece of equipment already uses this code.
     *
     * @param code the equipment code, for example {@code PROJECTOR}
     * @return {@code true} if the code is already taken
     */
    boolean existsByCode(String code);

    /**
     * Returns the equipment matching the given codes. Codes that do not exist are
     * simply absent from the result, which lets the caller detect unknown codes.
     *
     * @param codes the codes to look for
     * @return the equipment found
     */
    List<Equipment> findByCodeIn(Collection<String> codes);

    /**
     * Returns all equipment sorted by code.
     *
     * @return the sorted equipment
     */
    List<Equipment> findAllByOrderByCodeAsc();
}
