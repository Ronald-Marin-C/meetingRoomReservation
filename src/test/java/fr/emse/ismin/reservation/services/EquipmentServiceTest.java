package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.EquipmentRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.repositories.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link EquipmentService}. The repository is mocked, so no
 * database is involved.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @InjectMocks
    private EquipmentService equipmentService;

    private Equipment projector;
    private Equipment whiteboard;

    @BeforeEach
    void setUp() {
        projector = equipment(1L, "PROJECTOR", "Vidéoprojecteur");
        whiteboard = equipment(2L, "WHITEBOARD", "Tableau blanc");
    }

    @Test
    void testCreateEquipmentWhenCodeIsFree() {
        // GIVEN no equipment with the code PROJECTOR
        when(equipmentRepository.existsByCode("PROJECTOR")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN creating it
        Equipment created = equipmentService.create(new EquipmentRequest("PROJECTOR", "Vidéoprojecteur"));

        // THEN the equipment is saved with the requested values
        assertEquals("PROJECTOR", created.getCode());
        assertEquals("Vidéoprojecteur", created.getLabel());
        verify(equipmentRepository).save(created);
    }

    @Test
    void testCreateEquipmentWhenCodeIsAlreadyUsed() {
        // GIVEN an equipment already using the code PROJECTOR
        when(equipmentRepository.existsByCode("PROJECTOR")).thenReturn(true);

        // WHEN creating another one with the same code
        ConflictException exception = assertThrows(ConflictException.class,
                () -> equipmentService.create(new EquipmentRequest("PROJECTOR", "Projecteur")));

        // THEN the creation is refused and nothing is saved
        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void testFindAllByCodesWhenNoCodeIsRequested() {
        // GIVEN no requested equipment
        // WHEN looking for an empty list and for a missing list
        Set<Equipment> fromEmptyList = equipmentService.findAllByCodes(List.of());
        Set<Equipment> fromNull = equipmentService.findAllByCodes(null);

        // THEN nothing is required and the database is not queried
        assertTrue(fromEmptyList.isEmpty());
        assertTrue(fromNull.isEmpty());
        verify(equipmentRepository, never()).findByCodeIn(anyCollection());
    }

    @Test
    void testFindAllByCodesWhenEveryCodeExists() {
        // GIVEN two existing codes, one of them sent twice
        when(equipmentRepository.findByCodeIn(Set.of("PROJECTOR", "WHITEBOARD")))
                .thenReturn(List.of(projector, whiteboard));

        // WHEN looking for them
        Set<Equipment> found = equipmentService.findAllByCodes(List.of("PROJECTOR", "WHITEBOARD", "PROJECTOR"));

        // THEN both pieces of equipment are returned once
        assertEquals(Set.of(projector, whiteboard), found);
    }

    @Test
    void testFindAllByCodesWhenACodeDoesNotExist() {
        // GIVEN PROJECTOR exists but VIDEO_CONFERENCE and SPEAKERS do not
        when(equipmentRepository.findByCodeIn(Set.of("VIDEO_CONFERENCE", "PROJECTOR", "SPEAKERS")))
                .thenReturn(List.of(projector));

        // WHEN looking for the three codes
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> equipmentService.findAllByCodes(List.of("VIDEO_CONFERENCE", "PROJECTOR", "SPEAKERS")));

        // THEN the first unknown code sent is reported
        assertEquals(ErrorCode.EQUIPMENT_NOT_FOUND, exception.getCode());
        assertEquals("VIDEO_CONFERENCE", exception.getDetails().get("equipmentCode"));
    }

    private static Equipment equipment(Long id, String code, String label) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode(code);
        equipment.setLabel(label);
        return equipment;
    }
}
