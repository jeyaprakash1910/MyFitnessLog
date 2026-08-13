package com.myfitnesslog.integration;

import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest;
import com.myfitnesslog.dto.request.DiscardWorkoutSessionRequest;
import com.myfitnesslog.service.WorkoutSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the standard error envelope (API_SPECIFICATION.md §9) is consistent
 * across resources and status codes: every field (timestamp, status, error,
 * message, path) is present and correct for 400, 404, and 409.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutSessionService sessionService;

    /** Asserts the full envelope shape for a given expected status/error/path. */
    private void assertEnvelope(ResultActions result, int statusCode, String error, String path) throws Exception {
        result
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value(path));
    }

    @Test
    void validationFailureReturns400Envelope() throws Exception {
        String path = "/api/v1/routines";
        ResultActions result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","name":"","displayOrder":0}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        assertEnvelope(result, 400, "Bad Request", path);
    }

    @Test
    void notFoundReturns404Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        String path = "/api/v1/routines/" + id;
        ResultActions result = mockMvc.perform(get(path)).andExpect(status().isNotFound());
        assertEnvelope(result, 404, "Not Found", path);
    }

    @Test
    void businessRuleViolationReturns409Envelope() throws Exception {
        UUID sessionId = sessionService.startWorkout(
                new StartWorkoutSessionRequest(UUID.randomUUID(), null, null, Instant.parse("2026-07-20T09:00:00Z"), null))
                .session().getId();
        // Discarded, then completed: still illegal, and now the example this test
        // uses. It previously completed then discarded, which became legal on
        // 2026-08-13 so that a workout logged by mistake can leave the record.
        // DISCARDED stays terminal, so this is the transition that still conflicts.
        sessionService.discardWorkout(sessionId,
                new DiscardWorkoutSessionRequest(Instant.parse("2026-07-20T10:00:00Z"), null));

        String path = "/api/v1/workout-sessions/" + sessionId + "/complete";
        ResultActions result = mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"2026-07-20T10:30:00Z","notes":null}
                                """))
                .andExpect(status().isConflict());
        assertEnvelope(result, 409, "Conflict", path);
    }

    @Test
    void malformedJsonReturns400Envelope() throws Exception {
        String path = "/api/v1/routines";
        ResultActions result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest());
        assertEnvelope(result, 400, "Bad Request", path);
    }
}
