package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.EquipmentRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.repositories.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business logic of the equipment: creation, listing, and lookup of the codes
 * sent when creating rooms or reservations.
 */
@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    /**
     * Creates a piece of equipment.
     *
     * @param request code and label of the equipment
     * @return the saved equipment
     * @throws ConflictException {@code RESOURCE_ALREADY_EXISTS} if the code is already used
     */
    @Transactional
    public Equipment create(EquipmentRequest request) {
        if (equipmentRepository.existsByCode(request.code())) {
            throw new ConflictException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Un équipement utilise déjà ce code", Map.of("code", request.code()));
        }
        Equipment equipment = new Equipment();
        equipment.setCode(request.code());
        equipment.setLabel(request.label());
        return equipmentRepository.save(equipment);
    }

    /**
     * @return all equipment, sorted by code
     */
    @Transactional(readOnly = true)
    public List<Equipment> findAll() {
        return equipmentRepository.findAllByOrderByCodeAsc();
    }

    /**
     * Returns the equipment matching every given code. Duplicated codes are
     * counted once, and a {@code null} or empty collection means that no
     * equipment is requested.
     *
     * @param codes the equipment codes, may be {@code null}
     * @return the matching equipment, empty if no code is given
     * @throws ResourceNotFoundException {@code EQUIPMENT_NOT_FOUND} naming the first unknown code
     */
    @Transactional(readOnly = true)
    public Set<Equipment> findAllByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        Set<String> requestedCodes = new LinkedHashSet<>(codes);
        Map<String, Equipment> equipmentByCode = equipmentRepository.findByCodeIn(requestedCodes).stream()
                .collect(Collectors.toMap(Equipment::getCode, Function.identity()));

        // Report the first unknown code in the order sent by the client
        for (String code : requestedCodes) {
            if (!equipmentByCode.containsKey(code)) {
                throw ResourceNotFoundException.equipment(code);
            }
        }
        return new LinkedHashSet<>(equipmentByCode.values());
    }
}
