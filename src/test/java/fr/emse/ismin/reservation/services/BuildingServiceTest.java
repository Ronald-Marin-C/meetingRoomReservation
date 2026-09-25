package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.BuildingRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.InvalidFieldException;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.repositories.BuildingRepository;
import fr.emse.ismin.reservation.repositories.OrganizerRepository;
import fr.emse.ismin.reservation.repositories.RoomRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link BuildingService}. The repositories are mocked, so no
 * database is involved.
 */
@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    private static final Long BUILDING_ID = 1L;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private OrganizerRepository organizerRepository;

    @InjectMocks
    private BuildingService buildingService;

    private Building building;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId(BUILDING_ID);
        building.setName("Bâtiment A");
        building.setNumberOfFloors(5);

        // By default the building exists and no room or organizer occupies it
        lenient().when(buildingRepository.findById(BUILDING_ID)).thenReturn(Optional.of(building));
        lenient().when(roomRepository.findHighestFloorInBuilding(BUILDING_ID)).thenReturn(Optional.empty());
        lenient().when(organizerRepository.findHighestFloorInBuilding(BUILDING_ID)).thenReturn(Optional.empty());
    }

    @Test
    void testCreateBuildingWhenNameIsFree() {
        // GIVEN no building named "Bâtiment B"
        when(buildingRepository.existsByNameIgnoreCase("Bâtiment B")).thenReturn(false);
        when(buildingRepository.save(any(Building.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN creating it with 3 floors
        Building created = buildingService.create(new BuildingRequest("Bâtiment B", 3));

        // THEN the building is saved with the requested values
        assertEquals("Bâtiment B", created.getName());
        assertEquals(3, created.getNumberOfFloors());
        verify(buildingRepository).save(created);
    }

    @Test
    void testCreateBuildingWhenNameIsAlreadyUsed() {
        // GIVEN a building already named "Bâtiment A", compared ignoring case
        when(buildingRepository.existsByNameIgnoreCase("bâtiment a")).thenReturn(true);

        // WHEN creating a building with the same name in lower case
        ConflictException exception = assertThrows(ConflictException.class,
                () -> buildingService.create(new BuildingRequest("bâtiment a", 3)));

        // THEN the creation is refused and nothing is saved
        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
        verify(buildingRepository, never()).save(any());
    }

    @Test
    void testFindBuildingWhenItDoesNotExist() {
        // GIVEN no building with id 42
        when(buildingRepository.findById(42L)).thenReturn(Optional.empty());

        // WHEN looking for it
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> buildingService.findById(42L));

        // THEN a BUILDING_NOT_FOUND error gives the missing id
        assertEquals(ErrorCode.BUILDING_NOT_FOUND, exception.getCode());
        assertEquals(42L, exception.getDetails().get("buildingId"));
    }

    @Test
    void testUpdateBuildingNameAndFloors() {
        // GIVEN an existing building of 5 floors with a room on floor 2
        when(buildingRepository.existsByNameIgnoreCaseAndIdNot("Bâtiment Principal", BUILDING_ID)).thenReturn(false);
        when(roomRepository.findHighestFloorInBuilding(BUILDING_ID)).thenReturn(Optional.of(2));

        // WHEN renaming it and reducing it to 3 floors
        Building updated = buildingService.update(BUILDING_ID, new BuildingRequest("Bâtiment Principal", 3));

        // THEN both values are replaced
        assertEquals("Bâtiment Principal", updated.getName());
        assertEquals(3, updated.getNumberOfFloors());
    }

    @Test
    void testUpdateBuildingWhenNameIsUsedByAnotherBuilding() {
        // GIVEN another building already named "Bâtiment B"
        when(buildingRepository.existsByNameIgnoreCaseAndIdNot("Bâtiment B", BUILDING_ID)).thenReturn(true);

        // WHEN renaming the building to "Bâtiment B"
        ConflictException exception = assertThrows(ConflictException.class,
                () -> buildingService.update(BUILDING_ID, new BuildingRequest("Bâtiment B", 5)));

        // THEN the update is refused and the building keeps its name
        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
        assertEquals("Bâtiment A", building.getName());
    }

    @Test
    void testUpdateBuildingWhenARoomIsOnARemovedFloor() {
        // GIVEN a room on floor 4 and an organizer on floor 2
        when(roomRepository.findHighestFloorInBuilding(BUILDING_ID)).thenReturn(Optional.of(4));
        when(organizerRepository.findHighestFloorInBuilding(BUILDING_ID)).thenReturn(Optional.of(2));

        // WHEN reducing the building to 4 floors (0 to 3)
        ConflictException exception = assertThrows(ConflictException.class,
                () -> buildingService.update(BUILDING_ID, new BuildingRequest("Bâtiment A", 4)));

        // THEN the update is refused and the highest occupied floor is reported
        assertEquals(ErrorCode.BUILDING_FLOOR_COUNT_CONFLICT, exception.getCode());
        assertEquals(4, exception.getDetails().get("highestOccupiedFloor"));
        assertEquals(5, building.getNumberOfFloors());
    }

    @Test
    void testUpdateBuildingWhenAnOrganizerIsOnARemovedFloor() {
        // GIVEN no room but an organizer on floor 3
        when(organizerRepository.findHighestFloorInBuilding(BUILDING_ID)).thenReturn(Optional.of(3));

        // WHEN reducing the building to 2 floors
        ConflictException exception = assertThrows(ConflictException.class,
                () -> buildingService.update(BUILDING_ID, new BuildingRequest("Bâtiment A", 2)));

        // THEN the update is refused because of the organizer
        assertEquals(ErrorCode.BUILDING_FLOOR_COUNT_CONFLICT, exception.getCode());
        assertEquals(3, exception.getDetails().get("highestOccupiedFloor"));
    }

    @Test
    void testUpdateBuildingKeepingExactlyTheHighestOccupiedFloor() {
        // GIVEN a room on floor 3
        when(roomRepository.findHighestFloorInBuilding(BUILDING_ID)).thenReturn(Optional.of(3));

        // WHEN reducing the building to 4 floors, so floor 3 still exists
        Building updated = buildingService.update(BUILDING_ID, new BuildingRequest("Bâtiment A", 4));

        // THEN the update is accepted
        assertEquals(4, updated.getNumberOfFloors());
    }

    @Test
    void testFindLocationOnGroundAndLastFloor() {
        // GIVEN a building of 5 floors, numbered from 0 to 4
        // WHEN locating something on the ground floor and on the last floor
        Building groundFloor = buildingService.findLocation(BUILDING_ID, 0);
        Building lastFloor = buildingService.findLocation(BUILDING_ID, 4);

        // THEN both floors are accepted
        assertEquals(building, groundFloor);
        assertEquals(building, lastFloor);
    }

    @Test
    void testFindLocationOnAFloorAboveTheBuilding() {
        // GIVEN a building of 5 floors, numbered from 0 to 4
        // WHEN locating something on floor 5
        InvalidFieldException exception = assertThrows(InvalidFieldException.class,
                () -> buildingService.findLocation(BUILDING_ID, 5));

        // THEN the floor field is refused with the valid range
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        assertEquals("doit être compris entre 0 et 4 pour ce bâtiment", exception.getFieldErrors().get("floor"));
    }

    @Test
    void testFindLocationInABuildingThatDoesNotExist() {
        // GIVEN no building with id 42
        when(buildingRepository.findById(42L)).thenReturn(Optional.empty());

        // WHEN locating something in it
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> buildingService.findLocation(42L, 0));

        // THEN a BUILDING_NOT_FOUND error is raised before checking the floor
        assertEquals(ErrorCode.BUILDING_NOT_FOUND, exception.getCode());
    }

    @Test
    void testUpdateBuildingWhenItDoesNotExist() {
        // GIVEN no building with id 42
        when(buildingRepository.findById(42L)).thenReturn(Optional.empty());

        // WHEN updating it
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> buildingService.update(42L, new BuildingRequest("Bâtiment C", 2)));

        // THEN a BUILDING_NOT_FOUND error is raised
        assertEquals(ErrorCode.BUILDING_NOT_FOUND, exception.getCode());
    }
}
