package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.models.Reservation;
import fr.emse.ismin.reservation.models.ReservationStatus;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import fr.emse.ismin.reservation.repositories.BuildingRepository;
import fr.emse.ismin.reservation.repositories.EquipmentRepository;
import fr.emse.ismin.reservation.repositories.OrganizerRepository;
import fr.emse.ismin.reservation.repositories.ReservationRepository;
import fr.emse.ismin.reservation.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the {@code /api/rooms} endpoints, run against the
 * in-memory test database. Each test is rolled back at the end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomControllerTest {

    /** Searched period, far enough in the future to never be in the past. */
    private static final String START = "2030-10-15T14:00:00+02:00";
    private static final String END = "2030-10-15T16:00:00+02:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private OrganizerRepository organizerRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Building building;
    private Equipment projector;
    private Equipment whiteboard;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setName("Bâtiment A");
        building.setNumberOfFloors(5);
        buildingRepository.save(building);

        projector = saveEquipment("PROJECTOR", "Vidéoprojecteur");
        whiteboard = saveEquipment("WHITEBOARD", "Tableau blanc");
        saveEquipment("VIDEO_CONFERENCE", "Visioconférence");
    }

    @Test
    void testCreateRoomThenGetIt() throws Exception {
        // GIVEN a valid room with two pieces of equipment sent in reverse order
        String body = """
                {"name": "Orion", "buildingId": %d, "floor": 2, "capacity": 30,
                 "equipmentCodes": ["WHITEBOARD", "PROJECTOR"]}
                """.formatted(building.getId());

        // WHEN it is created
        String location = mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN it is available, located, and its equipment is sorted by code
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/rooms/\\d+")))
                .andExpect(jsonPath("$.name").value("Orion"))
                .andExpect(jsonPath("$.building.name").value("Bâtiment A"))
                .andExpect(jsonPath("$.floor").value(2))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.equipment.length()").value(2))
                .andExpect(jsonPath("$.equipment[0].code").value("PROJECTOR"))
                .andExpect(jsonPath("$.equipment[1].code").value("WHITEBOARD"))
                .andReturn().getResponse().getHeader("Location");

        // AND the room can be read at that URI
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Orion"))
                .andExpect(jsonPath("$.equipment.length()").value(2));
    }

    @Test
    void testCreateRoomWithUnknownEquipment() throws Exception {
        // GIVEN a room asking for equipment that does not exist
        String body = """
                {"name": "Orion", "buildingId": %d, "floor": 0, "capacity": 30, "equipmentCodes": ["HOLOGRAM"]}
                """.formatted(building.getId());

        // WHEN creating it
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the unknown code is reported
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.details.equipmentCode").value("HOLOGRAM"));
    }

    @Test
    void testCreateRoomWithBadlyFormattedEquipmentCode() throws Exception {
        // GIVEN an equipment code in lower case
        String body = """
                {"name": "Orion", "buildingId": %d, "floor": 0, "capacity": 30, "equipmentCodes": ["projector"]}
                """.formatted(building.getId());

        // WHEN creating the room
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the faulty item of the list is reported
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors['equipmentCodes[0]']").exists());
    }

    @Test
    void testCreateRoomOnAFloorOutsideTheBuilding() throws Exception {
        // GIVEN a building of 5 floors, numbered from 0 to 4
        String body = """
                {"name": "Orion", "buildingId": %d, "floor": 5, "capacity": 30}
                """.formatted(building.getId());

        // WHEN creating a room on floor 5
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the floor is refused
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.floor").value("doit être compris entre 0 et 4 pour ce bâtiment"));
    }

    @Test
    void testCreateRoomWithNameAlreadyUsedIgnoringCase() throws Exception {
        // GIVEN a room named "Salle Étoile"
        saveRoom("Salle Étoile", 30, 0);

        // WHEN creating "SALLE ÉTOILE"
        String body = """
                {"name": "SALLE ÉTOILE", "buildingId": %d, "floor": 0, "capacity": 10}
                """.formatted(building.getId());
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the creation is refused
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void testListRoomsSortedByNameIgnoringCase() throws Exception {
        // GIVEN three rooms created in a random order
        saveRoom("charlie", 10, 0);
        saveRoom("Alpha", 10, 0);
        saveRoom("bravo", 10, 0);

        // WHEN listing the rooms
        mockMvc.perform(get("/api/rooms"))
                // THEN they are sorted by name without considering case
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[1].name").value("bravo"))
                .andExpect(jsonPath("$[2].name").value("charlie"));
    }

    @Test
    void testGetRoomThatDoesNotExist() throws Exception {
        // GIVEN no room with id 999
        // WHEN reading it
        mockMvc.perform(get("/api/rooms/999"))
                // THEN the error follows the contract
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("La salle 999 n'existe pas"))
                .andExpect(jsonPath("$.details.roomId").value(999));
    }

    @Test
    void testUpdateRoomKeepsStatusAndEquipment() throws Exception {
        // GIVEN a room in maintenance with a projector
        Room room = saveRoom("Orion", 30, 0);
        room.setStatus(RoomStatus.MAINTENANCE);
        room.getEquipment().add(projector);

        // WHEN replacing its name, floor and capacity
        String body = """
                {"name": "Orion II", "buildingId": %d, "floor": 3, "capacity": 32}
                """.formatted(building.getId());
        mockMvc.perform(put("/api/rooms/" + room.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the new values are returned and the status and equipment are kept
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Orion II"))
                .andExpect(jsonPath("$.floor").value(3))
                .andExpect(jsonPath("$.capacity").value(32))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.equipment[0].code").value("PROJECTOR"));
    }

    @Test
    void testPutRoomInMaintenance() throws Exception {
        // GIVEN an available room
        Room room = saveRoom("Orion", 30, 0);

        // WHEN putting it in maintenance
        mockMvc.perform(patch("/api/rooms/" + room.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MAINTENANCE\"}"))
                // THEN its new status is returned
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void testUpdateRoomWithUnknownStatus() throws Exception {
        // GIVEN an existing room
        Room room = saveRoom("Orion", 30, 0);

        // WHEN sending a status that does not exist
        mockMvc.perform(patch("/api/rooms/" + room.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CLOSED\"}"))
                // THEN the status field is refused
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void testReplaceRoomEquipment() throws Exception {
        // GIVEN a room with a projector
        Room room = saveRoom("Orion", 30, 0);
        room.getEquipment().add(projector);

        // WHEN replacing its equipment with a whiteboard and a video conference system
        mockMvc.perform(put("/api/rooms/" + room.getId() + "/equipment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentCodes\": [\"WHITEBOARD\", \"VIDEO_CONFERENCE\"]}"))
                // THEN the projector is gone and the new equipment is listed
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.length()").value(2))
                .andExpect(jsonPath("$.equipment[0].code").value("VIDEO_CONFERENCE"))
                .andExpect(jsonPath("$.equipment[1].code").value("WHITEBOARD"));
    }

    @Test
    void testReplaceRoomEquipmentWithAnEmptyList() throws Exception {
        // GIVEN a room with a projector and a whiteboard
        Room room = saveRoom("Orion", 30, 0);
        room.getEquipment().add(projector);
        room.getEquipment().add(whiteboard);

        // WHEN replacing its equipment with an empty list
        mockMvc.perform(put("/api/rooms/" + room.getId() + "/equipment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentCodes\": []}"))
                // THEN the room has no equipment anymore
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment").isEmpty());
    }

    @Test
    void testReplaceEquipmentOfARoomThatDoesNotExist() throws Exception {
        // GIVEN no room with id 999
        // WHEN replacing its equipment
        mockMvc.perform(put("/api/rooms/999/equipment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentCodes\": [\"PROJECTOR\"]}"))
                // THEN the missing room is reported
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/rooms/999/equipment"));
    }

    @Test
    void testSearchAvailableRoomsAndCheckTheirOrder() throws Exception {
        // GIVEN rooms of various sizes, one in maintenance and one already booked
        Room exactFit = saveRoom("Zephyr", 20, 0);
        Room medium = saveRoom("Andromède", 25, 1);
        Room large = saveRoom("Atlas", 50, 2);
        saveRoom("Mini", 10, 0);
        Room maintenance = saveRoom("Maintenance", 20, 0);
        maintenance.setStatus(RoomStatus.MAINTENANCE);
        Room busy = saveRoom("Busy", 20, 0);
        saveReservation(busy, "2030-10-15T15:00:00+02:00", "2030-10-15T17:00:00+02:00", ReservationStatus.CONFIRMED);

        // WHEN searching the rooms available for 20 participants
        mockMvc.perform(get("/api/rooms/available")
                        .param("start", START).param("end", END).param("capacity", "20"))
                // THEN only compatible rooms are returned, sorted by unused seats
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(exactFit.getId()))
                .andExpect(jsonPath("$[0].unusedCapacity").value(0))
                .andExpect(jsonPath("$[1].id").value(medium.getId()))
                .andExpect(jsonPath("$[1].unusedCapacity").value(5))
                .andExpect(jsonPath("$[2].id").value(large.getId()))
                .andExpect(jsonPath("$[2].unusedCapacity").value(30));
    }

    @Test
    void testSearchAvailableRoomsIgnoresConsecutiveAndCancelledReservations() throws Exception {
        // GIVEN a room booked just before the period, and a cancelled booking during it
        Room room = saveRoom("Orion", 20, 0);
        saveReservation(room, "2030-10-15T12:00:00+02:00", START, ReservationStatus.CONFIRMED);
        saveReservation(room, START, END, ReservationStatus.CANCELLED);

        // WHEN searching the rooms available over the period
        mockMvc.perform(get("/api/rooms/available")
                        .param("start", START).param("end", END).param("capacity", "20"))
                // THEN the room is still proposed
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Orion"));
    }

    @Test
    void testSearchAvailableRoomsWithRequiredEquipment() throws Exception {
        // GIVEN a room with a projector and a room without equipment
        Room withProjector = saveRoom("Orion", 30, 0);
        withProjector.getEquipment().add(projector);
        saveRoom("Vega", 30, 0);

        // WHEN searching the rooms with a projector
        mockMvc.perform(get("/api/rooms/available")
                        .param("start", START).param("end", END).param("capacity", "10")
                        .param("equipment", "PROJECTOR"))
                // THEN only the room with a projector is returned
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Orion"));
    }

    @Test
    void testSearchAvailableRoomsWithUnknownEquipment() throws Exception {
        // GIVEN a room with every existing piece of equipment
        Room room = saveRoom("Orion", 30, 0);
        room.getEquipment().add(projector);

        // WHEN searching the rooms with an equipment that does not exist
        mockMvc.perform(get("/api/rooms/available")
                        .param("start", START).param("end", END).param("capacity", "10")
                        .param("equipment", "HOLOGRAM"))
                // THEN no room matches
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testSearchAvailableRoomsWithEndBeforeStart() throws Exception {
        // GIVEN a period whose end is before its start
        // WHEN searching
        mockMvc.perform(get("/api/rooms/available")
                        .param("start", END).param("end", START).param("capacity", "10"))
                // THEN the period is refused
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_PERIOD"))
                .andExpect(jsonPath("$.message").value("La date de début doit être antérieure à la date de fin"))
                .andExpect(jsonPath("$.path").value("/api/rooms/available"));
    }

    @Test
    void testSearchAvailableRoomsWithZeroCapacity() throws Exception {
        // GIVEN a capacity of zero
        // WHEN searching
        mockMvc.perform(get("/api/rooms/available")
                        .param("start", START).param("end", END).param("capacity", "0"))
                // THEN the capacity is refused as in the contract example
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.capacity").value("doit être strictement positive"));
    }

    @Test
    void testSearchAvailableRoomsWithoutStart() throws Exception {
        // GIVEN no start date
        // WHEN searching
        mockMvc.perform(get("/api/rooms/available").param("end", END).param("capacity", "10"))
                // THEN the missing parameter is reported
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.start").value("est obligatoire"));
    }

    private Equipment saveEquipment(String code, String label) {
        Equipment equipment = new Equipment();
        equipment.setCode(code);
        equipment.setLabel(label);
        return equipmentRepository.save(equipment);
    }

    private Room saveRoom(String name, int capacity, int floor) {
        Room room = new Room();
        room.setName(name);
        room.setBuilding(building);
        room.setFloor(floor);
        room.setCapacity(capacity);
        return roomRepository.save(room);
    }

    private void saveReservation(Room room, String start, String end, ReservationStatus status) {
        Organizer organizer = organizerRepository.findAll().stream().findFirst().orElseGet(() -> {
            Organizer created = new Organizer();
            created.setName("Alice Martin");
            created.setEmail("alice.martin@example.org");
            created.setBuilding(building);
            created.setFloor(0);
            return organizerRepository.save(created);
        });
        Reservation reservation = new Reservation();
        reservation.setTitle("Réunion");
        reservation.setRoom(room);
        reservation.setOrganizer(organizer);
        reservation.setStart(OffsetDateTime.parse(start).toInstant());
        reservation.setEnd(OffsetDateTime.parse(end).toInstant());
        reservation.setNumberOfParticipants(5);
        reservation.setStatus(status);
        reservation.setCreatedAt(Instant.now());
        reservationRepository.save(reservation);
    }
}
