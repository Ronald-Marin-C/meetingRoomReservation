package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Organizer;
import fr.emse.ismin.reservation.repositories.BuildingRepository;
import fr.emse.ismin.reservation.repositories.OrganizerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the {@code /api/organizers} endpoints, run against the
 * in-memory test database. Each test is rolled back at the end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private OrganizerRepository organizerRepository;

    private Building building;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setName("Bâtiment A");
        building.setNumberOfFloors(5);
        buildingRepository.save(building);
    }

    @Test
    void testCreateOrganizerThenGetIt() throws Exception {
        // GIVEN a valid organizer on floor 2 of an existing building
        String body = organizerJson("Alice Martin", "alice.martin@example.org", building.getId(), 2);

        // WHEN it is created
        String location = mockMvc.perform(post("/api/organizers").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the response is 201 with the organizer, its building and its URI
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/organizers/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alice Martin"))
                .andExpect(jsonPath("$.email").value("alice.martin@example.org"))
                .andExpect(jsonPath("$.floor").value(2))
                .andExpect(jsonPath("$.building.id").value(building.getId()))
                .andExpect(jsonPath("$.building.name").value("Bâtiment A"))
                .andExpect(jsonPath("$.building.numberOfFloors").value(5))
                .andReturn().getResponse().getHeader("Location");

        // AND the organizer can be read at that URI
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice.martin@example.org"));
    }

    @Test
    void testCreateOrganizerWithEmailAlreadyUsedIgnoringCase() throws Exception {
        // GIVEN an organizer using alice.martin@example.org
        saveOrganizer("Alice Martin", "alice.martin@example.org");

        // WHEN creating another organizer with the same email in upper case
        mockMvc.perform(post("/api/organizers").contentType(MediaType.APPLICATION_JSON)
                        .content(organizerJson("Alice M.", "ALICE.MARTIN@EXAMPLE.ORG", building.getId(), 0)))
                // THEN the creation is refused
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void testCreateOrganizerInABuildingThatDoesNotExist() throws Exception {
        // GIVEN no building with id 999
        // WHEN creating an organizer in it
        mockMvc.perform(post("/api/organizers").contentType(MediaType.APPLICATION_JSON)
                        .content(organizerJson("Alice Martin", "alice.martin@example.org", 999L, 0)))
                // THEN the missing building is reported
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILDING_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/organizers"))
                .andExpect(jsonPath("$.details.buildingId").value(999));
    }

    @Test
    void testCreateOrganizerOnAFloorOutsideTheBuilding() throws Exception {
        // GIVEN a building of 5 floors, numbered from 0 to 4
        // WHEN creating an organizer on floor 5
        mockMvc.perform(post("/api/organizers").contentType(MediaType.APPLICATION_JSON)
                        .content(organizerJson("Alice Martin", "alice.martin@example.org", building.getId(), 5)))
                // THEN the floor field is refused
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.floor").value("doit être compris entre 0 et 4 pour ce bâtiment"));
    }

    @Test
    void testCreateOrganizerWithInvalidEmail() throws Exception {
        // GIVEN an email without @
        // WHEN creating the organizer
        mockMvc.perform(post("/api/organizers").contentType(MediaType.APPLICATION_JSON)
                        .content(organizerJson("Alice Martin", "alice.martin", building.getId(), 0)))
                // THEN the email field is refused
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").value("doit être une adresse e-mail valide"));
    }

    @Test
    void testListOrganizersSortedByNameThenId() throws Exception {
        // GIVEN three organizers, two of them with the same name
        saveOrganizer("Bruno", "bruno@example.org");
        Organizer firstAlice = saveOrganizer("Alice", "alice.1@example.org");
        Organizer secondAlice = saveOrganizer("Alice", "alice.2@example.org");

        // WHEN listing the organizers
        mockMvc.perform(get("/api/organizers"))
                // THEN they are sorted by name, then by id
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(firstAlice.getId()))
                .andExpect(jsonPath("$[1].id").value(secondAlice.getId()))
                .andExpect(jsonPath("$[2].name").value("Bruno"));
    }

    @Test
    void testGetOrganizerThatDoesNotExist() throws Exception {
        // GIVEN no organizer with id 999
        // WHEN reading it
        mockMvc.perform(get("/api/organizers/999"))
                // THEN the error follows the contract
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORGANIZER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("L'organisateur 999 n'existe pas"))
                .andExpect(jsonPath("$.details.organizerId").value(999));
    }

    private Organizer saveOrganizer(String name, String email) {
        Organizer organizer = new Organizer();
        organizer.setName(name);
        organizer.setEmail(email);
        organizer.setBuilding(building);
        organizer.setFloor(0);
        return organizerRepository.save(organizer);
    }

    private static String organizerJson(String name, String email, Long buildingId, int floor) {
        return """
                {"name": "%s", "email": "%s", "buildingId": %d, "floor": %d}
                """.formatted(name, email, buildingId, floor);
    }
}
