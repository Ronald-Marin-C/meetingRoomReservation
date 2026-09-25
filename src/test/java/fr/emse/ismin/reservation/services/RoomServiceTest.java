package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.CreateRoomRequest;
import fr.emse.ismin.reservation.dtos.UpdateRoomRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.InvalidReservationPeriodException;
import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import fr.emse.ismin.reservation.repositories.ReservationRepository;
import fr.emse.ismin.reservation.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link RoomService}. Repositories and the other services are
 * mocked; the compatibility rules are the real ones, since they need no database.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final Long BUILDING_ID = 1L;
    private static final Instant START = Instant.parse("2030-10-15T12:00:00Z");
    private static final Instant END = Instant.parse("2030-10-15T14:00:00Z");

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BuildingService buildingService;

    @Mock
    private EquipmentService equipmentService;

    @Mock
    private ReservationPeriodValidator periodValidator;

    @Spy
    private RoomCompatibility roomCompatibility = new RoomCompatibility();

    @InjectMocks
    private RoomService roomService;

    private Building building;
    private Equipment projector;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId(BUILDING_ID);
        building.setName("Bâtiment A");
        building.setNumberOfFloors(5);

        projector = new Equipment();
        projector.setId(1L);
        projector.setCode("PROJECTOR");
        projector.setLabel("Vidéoprojecteur");
    }

    @Test
    void testCreateRoomWithEquipment() {
        // GIVEN an existing building, an existing projector and a free name
        when(buildingService.findLocation(BUILDING_ID, 2)).thenReturn(building);
        when(equipmentService.findAllByCodes(List.of("PROJECTOR"))).thenReturn(Set.of(projector));
        when(roomRepository.existsByNameIgnoreCase("Orion")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN creating a room of 30 seats with a projector
        Room created = roomService.create(new CreateRoomRequest("Orion", BUILDING_ID, 2, 30, List.of("PROJECTOR")));

        // THEN the room is saved, available, with its location and equipment
        assertEquals("Orion", created.getName());
        assertEquals(building, created.getBuilding());
        assertEquals(2, created.getFloor());
        assertEquals(30, created.getCapacity());
        assertEquals(RoomStatus.AVAILABLE, created.getStatus());
        assertEquals(Set.of(projector), created.getEquipment());
    }

    @Test
    void testCreateRoomWhenNameIsAlreadyUsed() {
        // GIVEN a room already named "orion"
        when(buildingService.findLocation(BUILDING_ID, 2)).thenReturn(building);
        when(equipmentService.findAllByCodes(null)).thenReturn(Set.of());
        when(roomRepository.existsByNameIgnoreCase("ORION")).thenReturn(true);

        // WHEN creating a room named "ORION"
        ConflictException exception = assertThrows(ConflictException.class,
                () -> roomService.create(new CreateRoomRequest("ORION", BUILDING_ID, 2, 30, null)));

        // THEN the creation is refused and nothing is saved
        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void testUpdateRoomKeepsStatusAndEquipment() {
        // GIVEN a room in maintenance with a projector
        Room room = room(7L, "Orion", 30);
        room.setStatus(RoomStatus.MAINTENANCE);
        room.getEquipment().add(projector);
        when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(buildingService.findLocation(BUILDING_ID, 3)).thenReturn(building);
        when(roomRepository.existsByNameIgnoreCaseAndIdNot("Orion 2", 7L)).thenReturn(false);

        // WHEN renaming it, moving it to floor 3 and enlarging it
        Room updated = roomService.update(7L, new UpdateRoomRequest("Orion 2", BUILDING_ID, 3, 40));

        // THEN the new values are set while the status and the equipment are kept
        assertEquals("Orion 2", updated.getName());
        assertEquals(3, updated.getFloor());
        assertEquals(40, updated.getCapacity());
        assertEquals(RoomStatus.MAINTENANCE, updated.getStatus());
        assertEquals(Set.of(projector), updated.getEquipment());
    }

    @Test
    void testPutRoomInMaintenance() {
        // GIVEN an available room
        Room room = room(7L, "Orion", 30);
        when(roomRepository.findById(7L)).thenReturn(Optional.of(room));

        // WHEN putting it in maintenance
        Room updated = roomService.updateStatus(7L, RoomStatus.MAINTENANCE);

        // THEN its status changes
        assertEquals(RoomStatus.MAINTENANCE, updated.getStatus());
    }

    @Test
    void testReplaceEquipmentWithAnEmptyList() {
        // GIVEN a room with a projector
        Room room = room(7L, "Orion", 30);
        room.getEquipment().add(projector);
        when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(equipmentService.findAllByCodes(List.of())).thenReturn(Set.of());

        // WHEN replacing its equipment with an empty list
        Room updated = roomService.replaceEquipment(7L, List.of());

        // THEN the room has no equipment anymore
        assertTrue(updated.getEquipment().isEmpty());
    }

    @Test
    void testFindAvailableRoomsSortedByUnusedSeatsThenNameThenId() {
        // GIVEN available rooms of various sizes for 20 participants
        Room large = room(1L, "Atlas", 50);
        Room exactFit = room(2L, "Zephyr", 20);
        Room mediumLowerCase = room(3L, "beta", 25);
        Room mediumUpperCase = room(4L, "Alpha", 25);
        Room sameNameHigherId = room(6L, "Alpha", 25);
        Room tooSmall = room(5L, "Mini", 10);
        Room busy = room(7L, "Busy", 20);
        when(roomRepository.findByStatus(RoomStatus.AVAILABLE)).thenReturn(
                List.of(large, exactFit, mediumLowerCase, sameNameHigherId, mediumUpperCase, tooSmall, busy));
        when(reservationRepository.findBusyRoomIds(START, END)).thenReturn(Set.of(7L));

        // WHEN searching the rooms available for 20 participants
        List<Room> available = roomService.findAvailable(START, END, 20, null);

        // THEN the busy and too small rooms are excluded and the others are sorted
        // by unused seats, then by name ignoring case, then by id
        assertEquals(List.of(exactFit, mediumUpperCase, sameNameHigherId, mediumLowerCase, large), available);
    }

    @Test
    void testFindAvailableRoomsWithAnInvalidPeriod() {
        // GIVEN a period refused by the validator
        doThrow(new InvalidReservationPeriodException("La date de début doit être antérieure à la date de fin"))
                .when(periodValidator).validate(END, START);

        // WHEN searching with that period
        // THEN the error is returned and no room is looked up
        assertThrows(InvalidReservationPeriodException.class,
                () -> roomService.findAvailable(END, START, 20, null));
        verify(roomRepository, never()).findByStatus(any());
    }

    private Room room(Long id, String name, int capacity) {
        Room room = new Room();
        room.setId(id);
        room.setName(name);
        room.setBuilding(building);
        room.setFloor(0);
        room.setCapacity(capacity);
        return room;
    }
}
