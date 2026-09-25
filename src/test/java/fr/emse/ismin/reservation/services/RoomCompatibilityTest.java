package fr.emse.ismin.reservation.services;

import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of {@link RoomCompatibility}. Rooms are built in memory, without
 * any database.
 */
class RoomCompatibilityTest {

    private static final Long ROOM_ID = 7L;

    private final RoomCompatibility roomCompatibility = new RoomCompatibility();

    private Room room;

    @BeforeEach
    void setUp() {
        // An available room of 30 seats with a projector and a whiteboard
        room = new Room();
        room.setId(ROOM_ID);
        room.setName("Orion");
        room.setCapacity(30);
        room.setStatus(RoomStatus.AVAILABLE);
        room.getEquipment().add(equipment("PROJECTOR"));
        room.getEquipment().add(equipment("WHITEBOARD"));
    }

    @Test
    void testRoomWithExactlyEnoughSeatsIsCompatible() {
        // GIVEN a room of 30 seats
        // WHEN checking it for 30 participants
        boolean compatible = roomCompatibility.isCompatible(room, 30, List.of(), Set.of());

        // THEN it is compatible and no seat is left
        assertTrue(compatible);
        assertEquals(0, roomCompatibility.unusedCapacity(room, 30));
    }

    @Test
    void testRoomTooSmallIsRejected() {
        // GIVEN a room of 30 seats
        // WHEN checking it for 31 participants
        boolean compatible = roomCompatibility.isCompatible(room, 31, List.of(), Set.of());

        // THEN it is rejected because of its capacity
        assertFalse(roomCompatibility.hasCapacity(room, 31));
        assertFalse(compatible);
    }

    @Test
    void testRoomInMaintenanceIsRejected() {
        // GIVEN a room in maintenance
        room.setStatus(RoomStatus.MAINTENANCE);

        // WHEN checking it for 10 participants
        boolean compatible = roomCompatibility.isCompatible(room, 10, List.of(), Set.of());

        // THEN it is rejected because it is not available
        assertFalse(roomCompatibility.isAvailable(room));
        assertFalse(compatible);
    }

    @Test
    void testRoomAlreadyReservedIsRejected() {
        // GIVEN a room already booked on the period
        Set<Long> busyRoomIds = Set.of(ROOM_ID);

        // WHEN checking it
        boolean compatible = roomCompatibility.isCompatible(room, 10, List.of(), busyRoomIds);

        // THEN it is rejected
        assertFalse(compatible);
    }

    @Test
    void testRoomWithoutARequestedEquipmentIsRejected() {
        // GIVEN a request for a projector and a video conference system
        List<String> requiredCodes = List.of("VIDEO_CONFERENCE", "PROJECTOR");

        // WHEN checking the room, which has no video conference system
        boolean compatible = roomCompatibility.isCompatible(room, 10, requiredCodes, Set.of());

        // THEN it is rejected and only the missing equipment is reported
        assertFalse(compatible);
        assertEquals(Set.of("VIDEO_CONFERENCE"), roomCompatibility.findMissingEquipment(room, requiredCodes));
    }

    @Test
    void testRoomWithExtraEquipmentIsCompatible() {
        // GIVEN a request for a projector only
        List<String> requiredCodes = List.of("PROJECTOR");

        // WHEN checking the room, which also has a whiteboard
        boolean compatible = roomCompatibility.isCompatible(room, 10, requiredCodes, Set.of());

        // THEN the extra equipment does not matter
        assertTrue(compatible);
    }

    @Test
    void testRoomIsCompatibleWhenNoEquipmentIsRequested() {
        // GIVEN a room without any equipment
        room.getEquipment().clear();

        // WHEN checking it with an empty list of equipment
        boolean compatible = roomCompatibility.isCompatible(room, 10, List.of(), Set.of());

        // THEN it is compatible
        assertTrue(compatible);
    }

    private static Equipment equipment(String code) {
        Equipment equipment = new Equipment();
        equipment.setCode(code);
        equipment.setLabel(code);
        return equipment;
    }
}
