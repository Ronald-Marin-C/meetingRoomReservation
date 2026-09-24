package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.models.Building;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.repositories.BuildingRepository;
import fr.emse.ismin.reservation.repositories.RoomRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the {@code /api/buildings} endpoints, run against the
 * in-memory test database. Each test is rolled back at the end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BuildingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void testCreateBuildingThenGetIt() throws Exception {
        // GIVEN a valid building
        String body = "{\"name\": \"Bâtiment A\", \"numberOfFloors\": 5}";

        // WHEN it is created
        String location = mockMvc.perform(post("/api/buildings").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the response is 201 with the building and its URI
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/buildings/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Bâtiment A"))
                .andExpect(jsonPath("$.numberOfFloors").value(5))
                .andReturn().getResponse().getHeader("Location");

        // AND the building can be read at that URI
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bâtiment A"));
    }

    @Test
    void testCreateBuildingWithNameAlreadyUsedIgnoringCase() throws Exception {
        // GIVEN an existing building named "Bâtiment A"
        saveBuilding("Bâtiment A", 5);

        // WHEN creating "BÂTIMENT A"
        mockMvc.perform(post("/api/buildings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"BÂTIMENT A\", \"numberOfFloors\": 2}"))
                // THEN the creation is refused
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void testCreateBuildingWithInvalidFields() throws Exception {
        // GIVEN a blank name and zero floors
        String body = "{\"name\": \"  \", \"numberOfFloors\": 0}";

        // WHEN creating the building
        mockMvc.perform(post("/api/buildings").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN both fields are reported
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/buildings"))
                .andExpect(jsonPath("$.fieldErrors.name").value("est obligatoire"))
                .andExpect(jsonPath("$.fieldErrors.numberOfFloors").value("doit être au moins égal à 1"));
    }

    @Test
    void testListBuildingsSortedByNameIgnoringCase() throws Exception {
        // GIVEN three buildings created in a random order
        saveBuilding("charlie", 1);
        saveBuilding("Alpha", 2);
        saveBuilding("bravo", 3);

        // WHEN listing the buildings
        mockMvc.perform(get("/api/buildings"))
                // THEN they are sorted by name without considering case
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[1].name").value("bravo"))
                .andExpect(jsonPath("$[2].name").value("charlie"));
    }

    @Test
    void testGetBuildingThatDoesNotExist() throws Exception {
        // GIVEN no building with id 999
        // WHEN reading it
        mockMvc.perform(get("/api/buildings/999"))
                // THEN the error follows the contract
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILDING_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Le bâtiment 999 n'existe pas"))
                .andExpect(jsonPath("$.details.buildingId").value(999));
    }

    @Test
    void testUpdateBuilding() throws Exception {
        // GIVEN an existing building
        Building building = saveBuilding("Bâtiment A", 5);

        // WHEN renaming it and changing its floors
        mockMvc.perform(put("/api/buildings/" + building.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bâtiment Principal\", \"numberOfFloors\": 8}"))
                // THEN the new values are returned
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(building.getId()))
                .andExpect(jsonPath("$.name").value("Bâtiment Principal"))
                .andExpect(jsonPath("$.numberOfFloors").value(8));
    }

    @Test
    void testUpdateBuildingRemovingAnOccupiedFloor() throws Exception {
        // GIVEN a building of 5 floors with a room on floor 4
        Building building = saveBuilding("Bâtiment A", 5);
        Room room = new Room();
        room.setName("Orion");
        room.setBuilding(building);
        room.setFloor(4);
        room.setCapacity(30);
        roomRepository.save(room);

        // WHEN reducing it to 3 floors
        mockMvc.perform(put("/api/buildings/" + building.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bâtiment A\", \"numberOfFloors\": 3}"))
                // THEN the update is refused with the highest occupied floor
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUILDING_FLOOR_COUNT_CONFLICT"))
                .andExpect(jsonPath("$.details.highestOccupiedFloor").value(4));
    }

    @Test
    void testUpdateBuildingThatDoesNotExist() throws Exception {
        // GIVEN no building with id 999
        // WHEN updating it
        mockMvc.perform(put("/api/buildings/999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bâtiment Z\", \"numberOfFloors\": 2}"))
                // THEN a 404 is returned
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILDING_NOT_FOUND"));
    }

    private Building saveBuilding(String name, int numberOfFloors) {
        Building building = new Building();
        building.setName(name);
        building.setNumberOfFloors(numberOfFloors);
        return buildingRepository.save(building);
    }
}
