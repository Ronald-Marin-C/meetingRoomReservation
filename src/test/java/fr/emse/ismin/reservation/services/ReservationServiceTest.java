package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.dtos.CreateReservationRequest;
import fr.emse.ismin.reservation.exceptions.ConflictException;
import fr.emse.ismin.reservation.exceptions.ErrorCode;
import fr.emse.ismin.reservation.exceptions.InvalidReservationPeriodException;
import fr.emse.ismin.reservation.exceptions.ResourceNotFoundException;
import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.models.Reservation;
import fr.emse.ismin.reservation.models.ReservationStatus;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import fr.emse.ismin.reservation.repositories.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link ReservationService}. Repositories and the other services
 * are mocked; the compatibility rules are the real ones, since they need no database.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-15T14:00:00+02:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-10-15T16:00:00+02:00");
    private static final Long ROOM_ID = 7L;
    private static final Long ORGANIZER_ID = 12L;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomService roomService;

    @Mock
    private OrganizerService organizerService;

    @Mock
    private EquipmentService equipmentService;

    @Mock
    private ReservationPeriodValidator periodValidator;

    private ReservationService reservationService;

    private Room room;
    private Organizer organizer;
    private Equipment projector;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(reservationRepository, roomService, organizerService,
                equipmentService, periodValidator, new RoomCompatibility(), Clock.fixed(NOW, ZoneOffset.UTC));

        Building building = new Building();
        building.setId(1L);
        building.setName("Bâtiment A");
        building.setNumberOfFloors(5);

        projector = new Equipment();
        projector.setId(1L);
        projector.setCode("PROJECTOR");
        projector.setLabel("Vidéoprojecteur");

        // An available room of 30 seats with a projector
        room = new Room();
        room.setId(ROOM_ID);
        room.setName("Orion");
        room.setBuilding(building);
        room.setFloor(1);
        room.setCapacity(30);
        room.getEquipment().add(projector);

        organizer = new Organizer();
        organizer.setId(ORGANIZER_ID);
        organizer.setName("Alice Martin");
        organizer.setEmail("alice.martin@example.org");
        organizer.setBuilding(building);
        organizer.setFloor(0);

        lenient().when(roomService.findById(ROOM_ID)).thenReturn(room);
        lenient().when(organizerService.findById(ORGANIZER_ID)).thenReturn(organizer);
        lenient().when(equipmentService.findAllByCodes(null)).thenReturn(Set.of());
        lenient().when(equipmentService.findAllByCodes(List.of("PROJECTOR"))).thenReturn(Set.of(projector));
        lenient().when(reservationRepository.findConflictingReservations(anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testCreateReservationInACompatibleRoom() {
        // GIVEN a free room with a projector and a request for 25 people with a projector
        CreateReservationRequest request = request(25, List.of("PROJECTOR"));

        // WHEN booking the room
        Reservation reservation = reservationService.create(request);

        // THEN the reservation is confirmed with the requested values and the creation time
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(room, reservation.getRoom());
        assertEquals(organizer, reservation.getOrganizer());
        assertEquals(START.toInstant(), reservation.getStart());
        assertEquals(END.toInstant(), reservation.getEnd());
        assertEquals(25, reservation.getNumberOfParticipants());
        assertEquals(Set.of(projector), reservation.getRequiredEquipment());
        assertEquals(NOW, reservation.getCreatedAt());
    }

    @Test
    void testCreateReservationWithExactlyTheRoomCapacity() {
        // GIVEN a room of 30 seats
        // WHEN booking it for 30 people
        Reservation reservation = reservationService.create(request(30, null));

        // THEN the reservation is accepted
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }

    @Test
    void testCreateReservationInARoomInMaintenance() {
        // GIVEN a room in maintenance
        room.setStatus(RoomStatus.MAINTENANCE);

        // WHEN booking it
        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.create(request(10, null)));

        // THEN it is refused as unavailable and nothing is saved
        assertEquals(ErrorCode.ROOM_UNAVAILABLE, exception.getCode());
        assertEquals("La salle Orion est en maintenance", exception.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void testCreateReservationInATooSmallRoom() {
        // GIVEN a room of 30 seats
        // WHEN booking it for 31 people
        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.create(request(31, null)));

        // THEN it is refused with the capacity and the number of participants
        assertEquals(ErrorCode.ROOM_CAPACITY_EXCEEDED, exception.getCode());
        assertEquals(30, exception.getDetails().get("roomCapacity"));
        assertEquals(31, exception.getDetails().get("numberOfParticipants"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void testCreateReservationInARoomMissingEquipment() {
        // GIVEN a room with a projector only, and a request for a video conference system
        Equipment videoConference = new Equipment();
        videoConference.setCode("VIDEO_CONFERENCE");
        when(equipmentService.findAllByCodes(List.of("VIDEO_CONFERENCE", "PROJECTOR")))
                .thenReturn(Set.of(videoConference, projector));

        // WHEN booking the room
        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.create(request(10, List.of("VIDEO_CONFERENCE", "PROJECTOR"))));

        // THEN it is refused and only the missing equipment is reported
        assertEquals(ErrorCode.MISSING_REQUIRED_EQUIPMENT, exception.getCode());
        assertEquals(List.of("VIDEO_CONFERENCE"), exception.getDetails().get("missingEquipmentCodes"));
    }

    @Test
    void testCreateReservationInARoomAlreadyBooked() {
        // GIVEN a confirmed reservation 38 overlapping the requested period
        Reservation existing = new Reservation();
        existing.setId(38L);
        when(reservationRepository.findConflictingReservations(ROOM_ID, START.toInstant(), END.toInstant()))
                .thenReturn(List.of(existing));

        // WHEN booking the room
        ConflictException exception = assertThrows(ConflictException.class,
                () -> reservationService.create(request(10, null)));

        // THEN it is refused and the conflicting reservation is reported
        assertEquals(ErrorCode.ROOM_ALREADY_RESERVED, exception.getCode());
        assertEquals(38L, exception.getDetails().get("conflictingReservationId"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void testCreateReservationWithAnInvalidPeriod() {
        // GIVEN a period refused by the validator
        doThrow(new InvalidReservationPeriodException("Une réservation ne peut pas dépasser huit heures"))
                .when(periodValidator).validate(START.toInstant(), END.toInstant());

        // WHEN booking a room
        // THEN the period error is returned before looking for the room
        assertThrows(InvalidReservationPeriodException.class, () -> reservationService.create(request(10, null)));
        verify(roomService, never()).findById(any());
    }

    @Test
    void testListReservationsWithFromAfterTo() {
        // GIVEN a filter whose from is after its to
        Instant from = Instant.parse("2026-10-16T00:00:00Z");
        Instant to = Instant.parse("2026-10-15T00:00:00Z");

        // WHEN listing the reservations
        InvalidReservationPeriodException exception = assertThrows(InvalidReservationPeriodException.class,
                () -> reservationService.findAll(null, null, from, to));

        // THEN the filter is refused with the message of the contract
        assertEquals("Le paramètre from doit être antérieur au paramètre to", exception.getMessage());
    }

    @Test
    void testCancelConfirmedReservation() {
        // GIVEN a confirmed reservation
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));

        // WHEN cancelling it
        Reservation cancelled = reservationService.cancel(42L);

        // THEN it is cancelled
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void testCancelReservationTwice() {
        // GIVEN an already cancelled reservation
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));

        // WHEN cancelling it again
        ConflictException exception = assertThrows(ConflictException.class, () -> reservationService.cancel(42L));

        // THEN the second cancellation is refused
        assertEquals(ErrorCode.RESERVATION_ALREADY_CANCELLED, exception.getCode());
        assertEquals("La réservation 42 est déjà annulée", exception.getMessage());
    }

    @Test
    void testFindReservationThatDoesNotExist() {
        // GIVEN no reservation with id 42
        when(reservationRepository.findById(42L)).thenReturn(Optional.empty());

        // WHEN looking for it
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> reservationService.findById(42L));

        // THEN a RESERVATION_NOT_FOUND error gives the missing id
        assertEquals(ErrorCode.RESERVATION_NOT_FOUND, exception.getCode());
        assertEquals(42L, exception.getDetails().get("reservationId"));
    }

    private CreateReservationRequest request(int numberOfParticipants, List<String> equipmentCodes) {
        return new CreateReservationRequest("Soutenance de projet", ROOM_ID, ORGANIZER_ID, START, END,
                numberOfParticipants, equipmentCodes);
    }
}
