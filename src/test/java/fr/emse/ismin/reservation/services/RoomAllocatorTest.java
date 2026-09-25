package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.models.Reservation;
import fr.emse.ismin.reservation.models.ReservationStatus;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the automatic assignment ({@link RoomAllocator}). Every object is
 * built in memory: no database and no Spring context are involved.
 */
class RoomAllocatorTest {

    private static final Instant START = Instant.parse("2030-10-15T11:00:00Z");
    private static final Instant END = Instant.parse("2030-10-15T12:00:00Z");

    private final RoomAllocator roomAllocator = new RoomAllocator(new RoomCompatibility());

    private Building buildingA;
    private Building buildingB;
    private Organizer organizer;

    @BeforeEach
    void setUp() {
        buildingA = building(1L, "Bâtiment A");
        buildingB = building(2L, "Bâtiment B");

        // The organizer works on floor 1 of building A
        organizer = new Organizer();
        organizer.setId(1L);
        organizer.setName("Alice Martin");
        organizer.setBuilding(buildingA);
        organizer.setFloor(1);
    }

    @Test
    void testSelectRoomWithExactlyEnoughSeats() {
        // GIVEN a room of exactly 20 seats and a room of 21 seats, on the organizer floor
        Room exactFit = room(1L, "Orion", buildingA, 1, 20);
        Room oneSeatMore = room(2L, "Vega", buildingA, 1, 21);

        // WHEN assigning a room to 20 participants
        Optional<RoomAssignment> assignment = allocate(List.of(oneSeatMore, exactFit), List.of(), 20);

        // THEN the room of exactly 20 seats is chosen, with no empty seat
        assertEquals(exactFit, assignment.orElseThrow().room());
        assertEquals(0, assignment.orElseThrow().unusedCapacity());
    }

    @Test
    void testRejectRoomTooSmall() {
        // GIVEN a room of 19 seats on the organizer floor and a room of 20 seats elsewhere
        Room tooSmall = room(1L, "Orion", buildingA, 1, 19);
        Room bigEnough = room(2L, "Vega", buildingB, 3, 20);

        // WHEN assigning a room to 20 participants
        Optional<RoomAssignment> assignment = allocate(List.of(tooSmall, bigEnough), List.of(), 20);

        // THEN the too small room is never chosen, even though it is closer
        assertEquals(bigEnough, assignment.orElseThrow().room());
    }

    @Test
    void testRejectRoomInMaintenance() {
        // GIVEN the closest room in maintenance and another room further away
        Room inMaintenance = room(1L, "Orion", buildingA, 1, 20);
        inMaintenance.setStatus(RoomStatus.MAINTENANCE);
        Room available = room(2L, "Vega", buildingA, 4, 20);

        // WHEN assigning a room
        Optional<RoomAssignment> assignment = allocate(List.of(inMaintenance, available), List.of(), 20);

        // THEN the room in maintenance is skipped
        assertEquals(available, assignment.orElseThrow().room());
    }

    @Test
    void testRejectRoomAlreadyReserved() {
        // GIVEN the closest room booked from 10:30 to 11:30, overlapping 11:00-12:00
        Room booked = room(1L, "Orion", buildingA, 1, 20);
        Room free = room(2L, "Vega", buildingA, 4, 20);
        Reservation existing = reservation(booked, "2030-10-15T10:30:00Z", "2030-10-15T11:30:00Z",
                ReservationStatus.CONFIRMED);

        // WHEN assigning a room from 11:00 to 12:00
        Optional<RoomAssignment> assignment = allocate(List.of(booked, free), List.of(existing), 20);

        // THEN the booked room is skipped
        assertEquals(free, assignment.orElseThrow().room());
    }

    @Test
    void testRejectRoomWithoutARequestedEquipment() {
        // GIVEN the closest room without projector and another room with one
        Room withoutProjector = room(1L, "Orion", buildingA, 1, 20);
        Room withProjector = room(2L, "Vega", buildingA, 4, 20);
        withProjector.getEquipment().add(equipment("PROJECTOR"));

        // WHEN assigning a room with a projector
        Optional<RoomAssignment> assignment = roomAllocator.allocate(List.of(withoutProjector, withProjector),
                List.of(), organizer, 20, List.of("PROJECTOR"), START, END);

        // THEN only the room with a projector can be chosen
        assertEquals(withProjector, assignment.orElseThrow().room());
    }

    @Test
    void testAcceptTwoConsecutiveReservations() {
        // GIVEN a room booked from 10:00 to 11:00
        Room room = room(1L, "Orion", buildingA, 1, 20);
        Reservation before = reservation(room, "2030-10-15T10:00:00Z", "2030-10-15T11:00:00Z",
                ReservationStatus.CONFIRMED);

        // WHEN assigning a room from 11:00 to 12:00
        Optional<RoomAssignment> assignment = allocate(List.of(room), List.of(before), 20);

        // THEN the room is still free for the consecutive period
        assertEquals(room, assignment.orElseThrow().room());
    }

    @Test
    void testIgnoreCancelledReservation() {
        // GIVEN a room whose only reservation on the period was cancelled
        Room room = room(1L, "Orion", buildingA, 1, 20);
        Reservation cancelled = reservation(room, "2030-10-15T11:00:00Z", "2030-10-15T12:00:00Z",
                ReservationStatus.CANCELLED);

        // WHEN assigning a room over the same period
        Optional<RoomAssignment> assignment = allocate(List.of(room), List.of(cancelled), 20);

        // THEN the cancelled reservation does not block the room
        assertEquals(room, assignment.orElseThrow().room());
    }

    @Test
    void testDistanceInTheSameBuilding() {
        // GIVEN a room on floor 4 of the organizer building (organizer on floor 1)
        Room room = room(1L, "Orion", buildingA, 4, 20);

        // WHEN computing the distance
        int distance = roomAllocator.distance(room, organizer);

        // THEN it is the floor difference
        assertEquals(3, distance);
    }

    @Test
    void testDistanceBelowTheOrganizerInTheSameBuilding() {
        // GIVEN a room on the ground floor of the organizer building (organizer on floor 1)
        Room room = room(1L, "Orion", buildingA, 0, 20);

        // WHEN computing the distance
        int distance = roomAllocator.distance(room, organizer);

        // THEN it is the absolute floor difference
        assertEquals(1, distance);
    }

    @Test
    void testDistanceBetweenTwoBuildings() {
        // GIVEN a room on floor 3 of another building (organizer on floor 1)
        Room room = room(1L, "Orion", buildingB, 3, 20);

        // WHEN computing the distance
        int distance = roomAllocator.distance(room, organizer);

        // THEN it is 10 plus the floor difference
        assertEquals(12, distance);
    }

    @Test
    void testDistanceBetweenTwoBuildingsOnTheSameFloor() {
        // GIVEN a room on the organizer floor but in another building
        Room room = room(1L, "Orion", buildingB, 1, 20);

        // WHEN computing the distance
        int distance = roomAllocator.distance(room, organizer);

        // THEN changing building alone costs 10
        assertEquals(10, distance);
    }

    @Test
    void testScoreCombinesDistanceAndUnusedSeats() {
        // GIVEN a room one floor away with 25 seats, for 20 participants
        Room room = room(1L, "Orion", buildingA, 2, 25);

        // WHEN assigning it
        RoomAssignment assignment = allocate(List.of(room), List.of(), 20).orElseThrow();

        // THEN distance 1 and 5 empty seats give 1 * 10 + 5 = 15
        assertEquals(1, assignment.distance());
        assertEquals(5, assignment.unusedCapacity());
        assertEquals(15, assignment.score());
        assertEquals(15, roomAllocator.score(1, 5));
    }

    @Test
    void testSelectRoomWithTheLowestScore() {
        // GIVEN three compatible rooms for 20 participants (organizer on floor 1 of building A):
        // same floor with 35 seats: 0 * 10 + 15 = 15
        Room sameFloorLarger = room(1L, "Atlas", buildingA, 1, 35);
        // two floors away with 20 seats: 2 * 10 + 0 = 20
        Room twoFloorsAwayExact = room(2L, "Borealis", buildingA, 3, 20);
        // other building, same floor, 20 seats: 10 * 10 + 0 = 100
        Room otherBuildingExact = room(3L, "Cassiopée", buildingB, 1, 20);

        // WHEN ranking and assigning
        List<RoomAssignment> ranking = roomAllocator.rank(
                List.of(otherBuildingExact, twoFloorsAwayExact, sameFloorLarger),
                List.of(), organizer, 20, List.of(), START, END);

        // THEN the rooms are sorted by score and the lowest score wins
        assertEquals(List.of(15L, 20L, 100L), ranking.stream().map(RoomAssignment::score).toList());
        assertEquals(sameFloorLarger, ranking.getFirst().room());
        assertEquals(sameFloorLarger, allocate(List.of(otherBuildingExact, sameFloorLarger), List.of(), 20)
                .orElseThrow().room());
    }

    @Test
    void testBreakTieByNameIgnoringCase() {
        // GIVEN two rooms with the same score, named "beta" and "Alpha"
        Room beta = room(1L, "beta", buildingA, 1, 20);
        Room alpha = room(2L, "Alpha", buildingA, 1, 20);

        // WHEN assigning a room
        Optional<RoomAssignment> assignment = allocate(List.of(beta, alpha), List.of(), 20);

        // THEN "Alpha" is chosen: alphabetical order without considering case
        assertEquals(alpha, assignment.orElseThrow().room());
    }

    @Test
    void testBreakTieByIdWhenNamesAreEqual() {
        // GIVEN two rooms with the same score and the same name ignoring case
        Room higherId = room(8L, "orion", buildingA, 1, 20);
        Room lowerId = room(3L, "ORION", buildingA, 1, 20);

        // WHEN assigning a room
        Optional<RoomAssignment> assignment = allocate(List.of(higherId, lowerId), List.of(), 20);

        // THEN the room with the lowest id is chosen
        assertEquals(lowerId, assignment.orElseThrow().room());
    }

    @Test
    void testNoCompatibleRoom() {
        // GIVEN only a room too small and a room in maintenance
        Room tooSmall = room(1L, "Orion", buildingA, 1, 10);
        Room inMaintenance = room(2L, "Vega", buildingA, 1, 50);
        inMaintenance.setStatus(RoomStatus.MAINTENANCE);

        // WHEN assigning a room to 20 participants
        Optional<RoomAssignment> assignment = allocate(List.of(tooSmall, inMaintenance), List.of(), 20);

        // THEN no room is assigned
        assertTrue(assignment.isEmpty());
    }

    private Optional<RoomAssignment> allocate(List<Room> rooms, List<Reservation> reservations, int participants) {
        return roomAllocator.allocate(rooms, reservations, organizer, participants, List.of(), START, END);
    }

    private static Building building(Long id, String name) {
        Building building = new Building();
        building.setId(id);
        building.setName(name);
        building.setNumberOfFloors(10);
        return building;
    }

    private static Room room(Long id, String name, Building building, int floor, int capacity) {
        Room room = new Room();
        room.setId(id);
        room.setName(name);
        room.setBuilding(building);
        room.setFloor(floor);
        room.setCapacity(capacity);
        room.setStatus(RoomStatus.AVAILABLE);
        return room;
    }

    private static Reservation reservation(Room room, String start, String end, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setRoom(room);
        reservation.setStart(Instant.parse(start));
        reservation.setEnd(Instant.parse(end));
        reservation.setStatus(status);
        return reservation;
    }

    private static Equipment equipment(String code) {
        Equipment equipment = new Equipment();
        equipment.setCode(code);
        equipment.setLabel(code);
        return equipment;
    }
}
