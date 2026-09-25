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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of {@code POST /api/reservations/automatic}, run against the
 * in-memory test database. Each test is rolled back at the end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutomaticReservationControllerTest {

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

    private Building buildingA;
    private Building buildingB;
    private Organizer alice;
    private Equipment projector;

    @BeforeEach
    void setUp() {
        buildingA = saveBuilding("Bâtiment A");
        buildingB = saveBuilding("Bâtiment B");
        projector = new Equipment();
        projector.setCode("PROJECTOR");
        projector.setLabel("Vidéoprojecteur");
        equipmentRepository.save(projector);

        // Alice works on floor 1 of building A
        alice = new Organizer();
        alice.setName("Alice Martin");
        alice.setEmail("alice.martin@example.org");
        alice.setBuilding(buildingA);
        alice.setFloor(1);
        organizerRepository.save(alice);
    }

    @Test
    void testCreateAutomaticReservationInTheBestRoom() throws Exception {
        // GIVEN for 20 people: Atlas, same floor, 35 seats (score 15), Borealis, two floors up,
        // 20 seats (score 20), and Cassiopée, other building, same floor, 20 seats (score 100)
        Room atlas = saveRoom("Atlas", buildingA, 1, 35);
        saveRoom("Borealis", buildingA, 3, 20);
        saveRoom("Cassiopée", buildingB, 1, 20);

        // WHEN asking for an automatic reservation
        bookAutomatically(20, "")
                // THEN Atlas, the lowest score, is booked and the reservation URI is returned
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/reservations/\\d+")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.room.id").value(atlas.getId()))
                .andExpect(jsonPath("$.room.name").value("Atlas"))
                .andExpect(jsonPath("$.organizer.id").value(alice.getId()))
                .andExpect(jsonPath("$.numberOfParticipants").value(20))
                .andExpect(jsonPath("$.start").value("2030-10-15T12:00:00Z"));
    }

    @Test
    void testCreateAutomaticReservationThenReadIt() throws Exception {
        // GIVEN a single compatible room
        saveRoom("Orion", buildingA, 1, 20);

        // WHEN asking for an automatic reservation
        String location = bookAutomatically(10, "").andReturn().getResponse().getHeader("Location");

        // THEN the reservation can be read at the returned URI
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.name").value("Orion"));
    }

    @Test
    void testCreateAutomaticReservationSkipsBookedAndMaintenanceRooms() throws Exception {
        // GIVEN the two best rooms unusable: one in maintenance, one already booked
        Room inMaintenance = saveRoom("Atlas", buildingA, 1, 20);
        inMaintenance.setStatus(RoomStatus.MAINTENANCE);
        Room booked = saveRoom("Borealis", buildingA, 1, 20);
        Room remaining = saveRoom("Cassiopée", buildingA, 4, 20);
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(chosenRoomJson(booked.getId())))
                .andExpect(status().isCreated());

        // WHEN asking for an automatic reservation over the same period
        bookAutomatically(20, "")
                // THEN the only remaining compatible room is booked
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room.id").value(remaining.getId()));
    }

    @Test
    void testCreateAutomaticReservationWithRequiredEquipment() throws Exception {
        // GIVEN the closest room without projector and a further room with one
        saveRoom("Atlas", buildingA, 1, 20);
        Room withProjector = saveRoom("Borealis", buildingB, 1, 20);
        withProjector.getEquipment().add(projector);

        // WHEN asking for an automatic reservation with a projector
        bookAutomatically(20, "\"PROJECTOR\"")
                // THEN the room with a projector is booked despite the building change
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room.id").value(withProjector.getId()))
                .andExpect(jsonPath("$.requiredEquipmentCodes[0]").value("PROJECTOR"));
    }

    @Test
    void testCreateAutomaticReservationBreaksTiesByName() throws Exception {
        // GIVEN two rooms with the same score, created as "zeta" then "Epsilon"
        saveRoom("zeta", buildingA, 1, 20);
        Room epsilon = saveRoom("Epsilon", buildingA, 1, 20);

        // WHEN asking for an automatic reservation
        bookAutomatically(20, "")
                // THEN "Epsilon" comes first alphabetically, ignoring case
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room.id").value(epsilon.getId()));
    }

    @Test
    void testCreateAutomaticReservationWhenNoRoomIsCompatible() throws Exception {
        // GIVEN only a room of 10 seats
        saveRoom("Orion", buildingA, 1, 10);

        // WHEN asking for an automatic reservation for 20 people
        bookAutomatically(20, "")
                // THEN no room is found, as in the contract example
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_COMPATIBLE_ROOM"))
                .andExpect(jsonPath("$.message").value("Aucune salle disponible ne correspond aux critères demandés"))
                .andExpect(jsonPath("$.path").value("/api/reservations/automatic"))
                .andExpect(jsonPath("$.details").isEmpty());

        // AND no reservation has been created
        mockMvc.perform(get("/api/reservations"))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testCreateAutomaticReservationWithMissingResources() throws Exception {
        // GIVEN an organizer and an equipment code that do not exist
        saveRoom("Orion", buildingA, 1, 20);
        String unknownOrganizer = automaticJson(999L, START, END, 5, "");
        String unknownEquipment = automaticJson(alice.getId(), START, END, 5, "\"HOLOGRAM\"");

        // WHEN asking for an automatic reservation with each of them
        // THEN each missing resource is reported with its own code
        mockMvc.perform(post("/api/reservations/automatic").contentType(MediaType.APPLICATION_JSON)
                        .content(unknownOrganizer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZER_NOT_FOUND"))
                .andExpect(jsonPath("$.details.organizerId").value(999));
        mockMvc.perform(post("/api/reservations/automatic").contentType(MediaType.APPLICATION_JSON)
                        .content(unknownEquipment))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.details.equipmentCode").value("HOLOGRAM"));
    }

    @Test
    void testCreateAutomaticReservationLongerThanEightHours() throws Exception {
        // GIVEN a period of nine hours
        String body = automaticJson(alice.getId(), "2030-10-15T08:00:00+02:00", "2030-10-15T17:00:00+02:00", 5, "");

        // WHEN asking for an automatic reservation
        mockMvc.perform(post("/api/reservations/automatic").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the period is refused as in the contract example
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_PERIOD"))
                .andExpect(jsonPath("$.message").value("Une réservation ne peut pas dépasser huit heures"))
                .andExpect(jsonPath("$.path").value("/api/reservations/automatic"));
    }

    @Test
    void testCreateAutomaticReservationWithZeroParticipants() throws Exception {
        // GIVEN a request with zero participants
        // WHEN asking for an automatic reservation
        bookAutomatically(0, "")
                // THEN the field is refused as in the contract example
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.numberOfParticipants").value("doit être strictement positif"));
    }

    private ResultActions bookAutomatically(int numberOfParticipants, String equipmentCodes) throws Exception {
        return mockMvc.perform(post("/api/reservations/automatic").contentType(MediaType.APPLICATION_JSON)
                .content(automaticJson(alice.getId(), START, END, numberOfParticipants, equipmentCodes)));
    }

    private static String automaticJson(Long organizerId, String start, String end,
                                        int numberOfParticipants, String equipmentCodes) {
        return """
                {"title": "Soutenance de projet", "organizerId": %d, "start": "%s", "end": "%s",
                 "numberOfParticipants": %d, "requiredEquipmentCodes": [%s]}
                """.formatted(organizerId, start, end, numberOfParticipants, equipmentCodes);
    }

    private String chosenRoomJson(Long roomId) {
        return """
                {"title": "Réunion", "roomId": %d, "organizerId": %d, "start": "%s", "end": "%s",
                 "numberOfParticipants": 5}
                """.formatted(roomId, alice.getId(), START, END);
    }

    private Building saveBuilding(String name) {
        Building building = new Building();
        building.setName(name);
        building.setNumberOfFloors(5);
        return buildingRepository.save(building);
    }

    private Room saveRoom(String name, Building building, int floor, int capacity) {
        Room room = new Room();
        room.setName(name);
        room.setBuilding(building);
        room.setFloor(floor);
        room.setCapacity(capacity);
        return roomRepository.save(room);
    }
}
