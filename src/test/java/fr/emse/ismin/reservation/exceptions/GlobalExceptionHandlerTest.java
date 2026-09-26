package fr.emse.ismin.reservation.exceptions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks that every kind of error is turned into the {@code ApiErrorResponse}
 * format of the contract, with the right HTTP status and code.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testResourceNotFoundReturns404WithCodeAndDetails() throws Exception {
        // GIVEN an endpoint looking for a building that does not exist
        // WHEN it is called
        mockMvc.perform(get("/test/buildings/3"))
                // THEN the response is a 404 in the contract format
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILDING_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Le bâtiment 3 n'existe pas"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/buildings/3"))
                .andExpect(jsonPath("$.details.buildingId").value(3))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void testConflictReturns409WithDetails() throws Exception {
        // GIVEN an endpoint booking a room that is already reserved
        // WHEN it is called
        mockMvc.perform(get("/test/conflict"))
                // THEN the response is a 409 carrying the conflicting reservation
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_ALREADY_RESERVED"))
                .andExpect(jsonPath("$.details.roomId").value(7))
                .andExpect(jsonPath("$.details.conflictingReservationId").value(38));
    }

    @Test
    void testInvalidPeriodReturns400() throws Exception {
        // GIVEN an endpoint receiving a period longer than eight hours
        // WHEN it is called
        mockMvc.perform(get("/test/period"))
                // THEN the response is a 400 with the period error code
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_PERIOD"))
                .andExpect(jsonPath("$.message").value("Une réservation ne peut pas dépasser huit heures"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void testInvalidFieldReturns400WithFieldError() throws Exception {
        // GIVEN an endpoint receiving a floor that does not exist in the building
        // WHEN it is called
        mockMvc.perform(get("/test/floor"))
                // THEN the response is a validation error on the floor field
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.floor").value("doit être inférieur à 5"));
    }

    @Test
    void testInvalidBodyReturns400WithFieldErrors() throws Exception {
        // GIVEN a body with zero participants
        String body = "{\"numberOfParticipants\": 0, \"start\": \"2026-10-15T14:00:00+02:00\"}";

        // WHEN it is posted
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the response is a validation error on that field
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(InvalidFieldException.DEFAULT_MESSAGE))
                .andExpect(jsonPath("$.fieldErrors.numberOfParticipants").value("doit être strictement positif"));
    }

    @Test
    void testMalformedJsonReturns400() throws Exception {
        // GIVEN a body that is not valid JSON
        String body = "{\"numberOfParticipants\": ";

        // WHEN it is posted
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the response is a validation error
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void testBadlyFormattedDateReturns400OnThatField() throws Exception {
        // GIVEN a body whose start date is not a date
        String body = "{\"numberOfParticipants\": 3, \"start\": \"tomorrow\"}";

        // WHEN it is posted
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body))
                // THEN the error names the start field
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.start").value("a un format invalide"));
    }

    @Test
    void testMissingParameterReturns400() throws Exception {
        // GIVEN a search without the required capacity parameter
        // WHEN it is called
        mockMvc.perform(get("/test/search"))
                // THEN the error names the missing parameter
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.capacity").value("est obligatoire"));
    }

    @Test
    void testParameterBreakingConstraintReturns400() throws Exception {
        // GIVEN a search with a capacity of zero
        // WHEN it is called
        mockMvc.perform(get("/test/search").param("capacity", "0"))
                // THEN the error names the invalid parameter
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.capacity").value("doit être strictement positive"));
    }

    @Test
    void testNonNumericIdReturns400() throws Exception {
        // GIVEN a path id that is not a number
        // WHEN it is called
        mockMvc.perform(get("/test/buildings/abc"))
                // THEN the error names the path parameter
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.buildingId").value("a un format invalide"));
    }

    @Test
    void testConflictExceptionRefusesNonConflictCode() {
        // GIVEN a code that is not bound to HTTP 409
        ErrorCode notFoundCode = ErrorCode.ROOM_NOT_FOUND;

        // WHEN a conflict exception is created with it
        // THEN the creation is refused
        assertThrows(IllegalArgumentException.class,
                () -> new ConflictException(notFoundCode, "La salle 7 n'existe pas"));
    }

    /** Body used to trigger Bean Validation and parsing errors. */
    record TestBody(
            @NotNull @Min(value = 1, message = "doit être strictement positif") Integer numberOfParticipants,
            Instant start) {
    }

    /** Controller whose endpoints only throw the exceptions under test. */
    @RestController
    static class FailingController {

        @GetMapping("/test/buildings/{buildingId}")
        void findBuilding(@PathVariable Long buildingId) {
            throw ResourceNotFoundException.building(buildingId);
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException(ErrorCode.ROOM_ALREADY_RESERVED,
                    "La salle Orion est déjà réservée sur cette période",
                    Map.of("roomId", 7, "conflictingReservationId", 38));
        }

        @GetMapping("/test/period")
        void period() {
            throw new InvalidReservationPeriodException("Une réservation ne peut pas dépasser huit heures");
        }

        @GetMapping("/test/floor")
        void floor() {
            throw new InvalidFieldException("floor", "doit être inférieur à 5");
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody TestBody body) {
            // Reached only when the body is valid
        }

        @GetMapping("/test/search")
        List<String> search(
                @RequestParam @Min(value = 1, message = "doit être strictement positive") Integer capacity) {
            return List.of();
        }
    }
}
