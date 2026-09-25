package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.models.RoomStatus;
import fr.emse.ismin.reservation.repositories.BuildingRepository;
import fr.emse.ismin.reservation.repositories.EquipmentRepository;
import fr.emse.ismin.reservation.repositories.OrganizerRepository;
import fr.emse.ismin.reservation.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the {@code /api/reservations} endpoints, run against the
 * in-memory test database. Each test is rolled back at the end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationControllerTest {

    /** Booked period, far enough in the future to never be in the past. */
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

    private Room orion;
    private Room vega;
    private Organizer alice;
    private Organizer bruno;

    @BeforeEach
    void setUp() {
        Building building = new Building();
        building.setName("Bâtiment A");
        building.setNumberOfFloors(5);
        buildingRepository.save(building);

        Equipment projector = saveEquipment("PROJECTOR", "Vidéoprojecteur");
        Equipment whiteboard = saveEquipment("WHITEBOARD", "Tableau blanc");
        saveEquipment("VIDEO_CONFERENCE", "Visioconférence");

        // Orion: 30 seats with a projector and a whiteboard; Vega: 10 seats without equipment
        orion = saveRoom(building, "Orion", 30);
        orion.getEquipment().add(projector);
        orion.getEquipment().add(whiteboard);
        vega = saveRoom(building, "Vega", 10);

        alice = saveOrganizer(building, "Alice Martin", "alice.martin@example.org");
        bruno = saveOrganizer(building, "Bruno Petit", "bruno.petit@example.org");
    }

    @Test
    void testCreateReservation() throws Exception {
        // GIVEN a free room with a projector and a whiteboard
        String body = reservationJson(orion.getId(), alice.getId(), START, END, 25, "\"WHITEBOARD\", \"PROJECTOR\"");

        // WHEN booking it for 25 people with both pieces of equipment
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the reservation is confirmed and follows the contract
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/reservations/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Réunion d'équipe"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.room.id").value(orion.getId()))
                .andExpect(jsonPath("$.room.building.name").value("Bâtiment A"))
                .andExpect(jsonPath("$.room.equipment").doesNotExist())
                .andExpect(jsonPath("$.organizer.id").value(alice.getId()))
                .andExpect(jsonPath("$.organizer.email").doesNotExist())
                .andExpect(jsonPath("$.start").value("2030-10-15T12:00:00Z"))
                .andExpect(jsonPath("$.end").value("2030-10-15T14:00:00Z"))
                .andExpect(jsonPath("$.numberOfParticipants").value(25))
                .andExpect(jsonPath("$.requiredEquipmentCodes[0]").value("PROJECTOR"))
                .andExpect(jsonPath("$.requiredEquipmentCodes[1]").value("WHITEBOARD"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void testCreateThenGetReservation() throws Exception {
        // GIVEN a reservation just created
        String location = book(orion.getId(), START, END).andReturn().getResponse().getHeader("Location");

        // WHEN reading it at its URI
        mockMvc.perform(get(location))
                // THEN the same reservation is returned
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.name").value("Orion"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void testRefuseConflictingReservation() throws Exception {
        // GIVEN Orion booked from 14:00 to 16:00
        String existing = book(orion.getId(), START, END).andReturn().getResponse().getContentAsString();
        Long existingId = Long.valueOf(existing.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        // WHEN booking it from 15:00 to 17:00
        book(orion.getId(), "2030-10-15T15:00:00+02:00", "2030-10-15T17:00:00+02:00")
                // THEN the reservation is refused and the existing one is reported
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_ALREADY_RESERVED"))
                .andExpect(jsonPath("$.message").value("La salle Orion est déjà réservée sur cette période"))
                .andExpect(jsonPath("$.details.roomId").value(orion.getId()))
                .andExpect(jsonPath("$.details.conflictingReservationId").value(existingId));
    }

    @Test
    void testAcceptConsecutiveReservations() throws Exception {
        // GIVEN Orion booked from 14:00 to 16:00
        book(orion.getId(), START, END).andExpect(status().isCreated());

        // WHEN booking it from 16:00 to 17:00
        book(orion.getId(), END, "2030-10-15T17:00:00+02:00")
                // THEN the consecutive reservation is accepted
                .andExpect(status().isCreated());
    }

    @Test
    void testCancelThenBookTheSamePeriodAgain() throws Exception {
        // GIVEN Orion booked from 14:00 to 16:00
        String location = book(orion.getId(), START, END).andReturn().getResponse().getHeader("Location");

        // WHEN cancelling that reservation
        mockMvc.perform(patch(location + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // THEN the same period can be booked again
        book(orion.getId(), START, END)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void testCancelReservationTwice() throws Exception {
        // GIVEN a cancelled reservation
        String location = book(orion.getId(), START, END).andReturn().getResponse().getHeader("Location");
        mockMvc.perform(patch(location + "/cancel")).andExpect(status().isOk());

        // WHEN cancelling it again
        mockMvc.perform(patch(location + "/cancel"))
                // THEN the second cancellation is refused
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_CANCELLED"));
    }

    @Test
    void testCancelReservationThatDoesNotExist() throws Exception {
        // GIVEN no reservation with id 999
        // WHEN cancelling it
        mockMvc.perform(patch("/api/reservations/999/cancel"))
                // THEN the error follows the contract
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/reservations/999/cancel"))
                .andExpect(jsonPath("$.details.reservationId").value(999));
    }

    @Test
    void testCreateReservationInARoomInMaintenance() throws Exception {
        // GIVEN Orion in maintenance
        orion.setStatus(RoomStatus.MAINTENANCE);

        // WHEN booking it
        book(orion.getId(), START, END)
                // THEN the room is reported as unavailable
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"))
                .andExpect(jsonPath("$.details.roomId").value(orion.getId()));
    }

    @Test
    void testCreateReservationInATooSmallRoomIsNeverReplaced() throws Exception {
        // GIVEN Vega, a room of 10 seats, while Orion could host 25 people
        String body = reservationJson(vega.getId(), alice.getId(), START, END, 25, "");

        // WHEN booking Vega for 25 people
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the reservation is refused instead of moving to Orion
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.details.roomCapacity").value(10))
                .andExpect(jsonPath("$.details.numberOfParticipants").value(25));
    }

    @Test
    void testCreateReservationInARoomMissingEquipment() throws Exception {
        // GIVEN Orion, which has no video conference system
        String body = reservationJson(orion.getId(), alice.getId(), START, END, 5,
                "\"PROJECTOR\", \"VIDEO_CONFERENCE\"");

        // WHEN booking it with a video conference system
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the missing equipment is reported
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_EQUIPMENT"))
                .andExpect(jsonPath("$.details.missingEquipmentCodes[0]").value("VIDEO_CONFERENCE"))
                .andExpect(jsonPath("$.details.missingEquipmentCodes.length()").value(1));
    }

    @Test
    void testCreateReservationWithMissingResources() throws Exception {
        // GIVEN a room, an organizer and an equipment code that do not exist
        String unknownRoom = reservationJson(999L, alice.getId(), START, END, 5, "");
        String unknownOrganizer = reservationJson(orion.getId(), 999L, START, END, 5, "");
        String unknownEquipment = reservationJson(orion.getId(), alice.getId(), START, END, 5, "\"HOLOGRAM\"");

        // WHEN booking with each of them
        // THEN each missing resource is reported with its own code
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(unknownRoom))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(unknownOrganizer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZER_NOT_FOUND"));
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(unknownEquipment))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.details.equipmentCode").value("HOLOGRAM"));
    }

    @Test
    void testCreateReservationLongerThanEightHours() throws Exception {
        // GIVEN a period of nine hours
        // WHEN booking it
        book(orion.getId(), "2030-10-15T08:00:00+02:00", "2030-10-15T17:00:00+02:00")
                // THEN the period is refused as in the contract example
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_PERIOD"))
                .andExpect(jsonPath("$.message").value("Une réservation ne peut pas dépasser huit heures"))
                .andExpect(jsonPath("$.path").value("/api/reservations"));
    }

    @Test
    void testCreateReservationInThePast() throws Exception {
        // GIVEN a period in 2020
        // WHEN booking it
        book(orion.getId(), "2020-10-15T14:00:00+02:00", "2020-10-15T16:00:00+02:00")
                // THEN the period is refused
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_PERIOD"))
                .andExpect(jsonPath("$.message").value("La date de début ne peut pas être dans le passé"));
    }

    @Test
    void testCreateReservationWithZeroParticipants() throws Exception {
        // GIVEN a request with zero participants
        String body = reservationJson(orion.getId(), alice.getId(), START, END, 0, "");

        // WHEN booking
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the field is refused as in the contract example
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La requête contient des données invalides"))
                .andExpect(jsonPath("$.fieldErrors.numberOfParticipants").value("doit être strictement positif"));
    }

    @Test
    void testListReservationsWithFilters() throws Exception {
        // GIVEN reservations of two organizers in two rooms, one of them cancelled
        book(orion.getId(), alice.getId(), "2030-10-15T09:00:00Z", "2030-10-15T10:00:00Z");
        book(vega.getId(), alice.getId(), "2030-10-15T08:00:00Z", "2030-10-15T09:00:00Z");
        String cancelled = book(orion.getId(), bruno.getId(), "2030-10-16T09:00:00Z", "2030-10-16T10:00:00Z")
                .andReturn().getResponse().getHeader("Location");
        mockMvc.perform(patch(cancelled + "/cancel")).andExpect(status().isOk());

        // WHEN listing without filter
        // THEN every reservation is returned, cancelled included, sorted by start
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].room.name").value("Vega"))
                .andExpect(jsonPath("$[1].room.name").value("Orion"))
                .andExpect(jsonPath("$[2].status").value("CANCELLED"));

        // WHEN filtering by room and organizer together
        // THEN both filters are applied
        mockMvc.perform(get("/api/reservations")
                        .param("roomId", orion.getId().toString())
                        .param("organizerId", alice.getId().toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].start").value("2030-10-15T09:00:00Z"));

        // WHEN filtering on the morning of October 15th, 08:30 to 09:30
        // THEN the reservations overlapping that period are returned
        mockMvc.perform(get("/api/reservations")
                        .param("from", "2030-10-15T08:30:00Z")
                        .param("to", "2030-10-15T09:30:00Z"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testListReservationsWithFromAfterTo() throws Exception {
        // GIVEN a from date after the to date
        // WHEN listing the reservations
        mockMvc.perform(get("/api/reservations")
                        .param("from", "2030-10-16T00:00:00Z")
                        .param("to", "2030-10-15T00:00:00Z"))
                // THEN the filters are refused as in the contract example
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_PERIOD"))
                .andExpect(jsonPath("$.message").value("Le paramètre from doit être antérieur au paramètre to"));
    }

    @Test
    void testGetReservationThatDoesNotExist() throws Exception {
        // GIVEN no reservation with id 999
        // WHEN reading it
        mockMvc.perform(get("/api/reservations/999"))
                // THEN the error follows the contract
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("La réservation 999 n'existe pas"));
    }

    private ResultActions book(Long roomId, String start, String end) throws Exception {
        return book(roomId, alice.getId(), start, end);
    }

    private ResultActions book(Long roomId, Long organizerId, String start, String end) throws Exception {
        return mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(reservationJson(roomId, organizerId, start, end, 5, "")));
    }

    private static String reservationJson(Long roomId, Long organizerId, String start, String end,
                                          int numberOfParticipants, String equipmentCodes) {
        return """
                {"title": "Réunion d'équipe", "roomId": %d, "organizerId": %d,
                 "start": "%s", "end": "%s", "numberOfParticipants": %d,
                 "requiredEquipmentCodes": [%s]}
                """.formatted(roomId, organizerId, start, end, numberOfParticipants, equipmentCodes);
    }

    private Equipment saveEquipment(String code, String label) {
        Equipment equipment = new Equipment();
        equipment.setCode(code);
        equipment.setLabel(label);
        return equipmentRepository.save(equipment);
    }

    private Room saveRoom(Building building, String name, int capacity) {
        Room room = new Room();
        room.setName(name);
        room.setBuilding(building);
        room.setFloor(1);
        room.setCapacity(capacity);
        return roomRepository.save(room);
    }

    private Organizer saveOrganizer(Building building, String name, String email) {
        Organizer organizer = new Organizer();
        organizer.setName(name);
        organizer.setEmail(email);
        organizer.setBuilding(building);
        organizer.setFloor(0);
        return organizerRepository.save(organizer);
    }
}
