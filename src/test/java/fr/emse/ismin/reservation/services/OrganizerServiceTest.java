package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.OrganizerRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.InvalidFieldException;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.repositories.OrganizerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link OrganizerService}. The repository and the building
 * service are mocked, so no database is involved.
 */
@ExtendWith(MockitoExtension.class)
class OrganizerServiceTest {

    private static final Long BUILDING_ID = 1L;

    @Mock
    private OrganizerRepository organizerRepository;

    @Mock
    private BuildingService buildingService;

    @InjectMocks
    private OrganizerService organizerService;

    private Building building;
    private OrganizerRequest request;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId(BUILDING_ID);
        building.setName("Bâtiment A");
        building.setNumberOfFloors(5);

        request = new OrganizerRequest("Alice Martin", "alice.martin@example.org", BUILDING_ID, 2);
    }

    @Test
    void testCreateOrganizerInAnExistingBuilding() {
        // GIVEN an existing building with floor 2 and a free email
        when(buildingService.findLocation(BUILDING_ID, 2)).thenReturn(building);
        when(organizerRepository.existsByEmailIgnoreCase("alice.martin@example.org")).thenReturn(false);
        when(organizerRepository.save(any(Organizer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN creating the organizer
        Organizer created = organizerService.create(request);

        // THEN the organizer is saved with its location
        assertEquals("Alice Martin", created.getName());
        assertEquals("alice.martin@example.org", created.getEmail());
        assertEquals(building, created.getBuilding());
        assertEquals(2, created.getFloor());
        verify(organizerRepository).save(created);
    }

    @Test
    void testCreateOrganizerWhenEmailIsAlreadyUsed() {
        // GIVEN an organizer already using the same email written in upper case
        OrganizerRequest upperCaseEmail = new OrganizerRequest("Alice M.", "ALICE.MARTIN@EXAMPLE.ORG", BUILDING_ID, 2);
        when(buildingService.findLocation(BUILDING_ID, 2)).thenReturn(building);
        when(organizerRepository.existsByEmailIgnoreCase("ALICE.MARTIN@EXAMPLE.ORG")).thenReturn(true);

        // WHEN creating the organizer
        ConflictException exception = assertThrows(ConflictException.class,
                () -> organizerService.create(upperCaseEmail));

        // THEN the creation is refused and nothing is saved
        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
        verify(organizerRepository, never()).save(any());
    }

    @Test
    void testCreateOrganizerInABuildingThatDoesNotExist() {
        // GIVEN no building with the requested id
        when(buildingService.findLocation(BUILDING_ID, 2)).thenThrow(ResourceNotFoundException.building(BUILDING_ID));

        // WHEN creating the organizer
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> organizerService.create(request));

        // THEN the building error is returned and nothing is saved
        assertEquals(ErrorCode.BUILDING_NOT_FOUND, exception.getCode());
        verify(organizerRepository, never()).save(any());
    }

    @Test
    void testCreateOrganizerOnAFloorThatDoesNotExist() {
        // GIVEN a floor that does not exist in the building
        when(buildingService.findLocation(BUILDING_ID, 2))
                .thenThrow(new InvalidFieldException("floor", "doit être compris entre 0 et 1 pour ce bâtiment"));

        // WHEN creating the organizer
        InvalidFieldException exception = assertThrows(InvalidFieldException.class,
                () -> organizerService.create(request));

        // THEN the floor is refused and nothing is saved
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(organizerRepository, never()).save(any());
    }

    @Test
    void testFindOrganizerThatDoesNotExist() {
        // GIVEN no organizer with id 12
        when(organizerRepository.findById(12L)).thenReturn(Optional.empty());

        // WHEN looking for it
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> organizerService.findById(12L));

        // THEN an ORGANIZER_NOT_FOUND error gives the missing id
        assertEquals(ErrorCode.ORGANIZER_NOT_FOUND, exception.getCode());
        assertEquals(12L, exception.getDetails().get("organizerId"));
    }
}
