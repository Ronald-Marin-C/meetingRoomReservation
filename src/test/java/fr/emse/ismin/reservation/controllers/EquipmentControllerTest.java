package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.dtos.EquipmentRequest;
import fr.emse.ismin.reservation.models.Equipment;
import fr.emse.ismin.reservation.repositories.EquipmentRepository;
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
 * Integration tests of the {@code /api/equipment} endpoints, run against the
 * in-memory test database. Each test is rolled back at the end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EquipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Test
    void testCreateEquipment() throws Exception {
        // GIVEN a valid piece of equipment
        String body = "{\"code\": \"VIDEO_CONFERENCE\", \"label\": \"Visioconférence\"}";

        // WHEN it is created
        mockMvc.perform(post("/api/equipment").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the response is 201 with the equipment and its URI
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/equipment/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("VIDEO_CONFERENCE"))
                .andExpect(jsonPath("$.label").value("Visioconférence"));
    }

    @Test
    void testCreateEquipmentWithCodeAlreadyUsed() throws Exception {
        // GIVEN an existing PROJECTOR
        saveEquipment("PROJECTOR", "Vidéoprojecteur");

        // WHEN creating another PROJECTOR
        mockMvc.perform(post("/api/equipment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"PROJECTOR\", \"label\": \"Projecteur\"}"))
                // THEN the creation is refused
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void testCreateEquipmentWithInvalidCode() throws Exception {
        // GIVEN a code in lower case and no label
        String body = "{\"code\": \"projector\"}";

        // WHEN creating the equipment
        mockMvc.perform(post("/api/equipment").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN both fields are reported
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.code").value(EquipmentRequest.CODE_MESSAGE))
                .andExpect(jsonPath("$.fieldErrors.label").value("est obligatoire"));
    }

    @Test
    void testListEquipmentSortedByCode() throws Exception {
        // GIVEN three pieces of equipment created in a random order
        saveEquipment("WHITEBOARD", "Tableau blanc");
        saveEquipment("PROJECTOR", "Vidéoprojecteur");
        saveEquipment("MICROPHONE", "Micro");

        // WHEN listing the equipment
        mockMvc.perform(get("/api/equipment"))
                // THEN it is sorted by code
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("MICROPHONE"))
                .andExpect(jsonPath("$[1].code").value("PROJECTOR"))
                .andExpect(jsonPath("$[2].code").value("WHITEBOARD"));
    }

    private void saveEquipment(String code, String label) {
        Equipment equipment = new Equipment();
        equipment.setCode(code);
        equipment.setLabel(label);
        equipmentRepository.save(equipment);
    }
}
