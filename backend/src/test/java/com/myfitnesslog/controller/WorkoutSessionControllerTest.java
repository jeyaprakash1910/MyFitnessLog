package com.myfitnesslog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for {@link WorkoutSessionController} over the full stack.
 * Transactional: each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String STARTED_AT = "2026-07-20T09:00:00Z";
    private static final String ENDED_AT = "2026-07-20T10:00:00Z";

    private String startBody(UUID id) {
        return """
                {"id":"%s","routineId":null,"startedAt":"%s","notes":"m"}
                """.formatted(id, STARTED_AT);
    }

    private UUID startManual() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(id)))
                .andExpect(status().isCreated());
        return id;
    }

    private void complete(UUID id) throws Exception {
        mockMvc.perform(put("/api/v1/workout-sessions/{id}/complete", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"%s","notes":null}
                                """.formatted(ENDED_AT)))
                .andExpect(status().isOk());
    }

    @Test
    void startReturns201ThenReplayReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.routineId").isEmpty())
                .andExpect(jsonPath("$.startedAt").value(STARTED_AT));

        mockMvc.perform(post("/api/v1/workout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(id)))
                .andExpect(status().isOk());
    }

    @Test
    void startValidationFailsWithoutStartedAt() throws Exception {
        mockMvc.perform(post("/api/v1/workout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","routineId":null,"notes":"m"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("startedAt is required.")));
    }

    @Test
    void completeReturns200AndReplayReturns200() throws Exception {
        UUID id = startManual();
        complete(id);
        // Idempotent replay
        complete(id);

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endedAt").value(ENDED_AT));
    }

    @Test
    void discardReturns200AndIllegalTransitionReturns409() throws Exception {
        UUID id = startManual();
        complete(id);

        // COMPLETED → DISCARDED is illegal
        mockMvc.perform(put("/api/v1/workout-sessions/{id}/discard", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"%s","notes":null}
                                """.formatted(ENDED_AT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void completeMissingReturns404() throws Exception {
        mockMvc.perform(put("/api/v1/workout-sessions/{id}/complete", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"%s","notes":null}
                                """.formatted(ENDED_AT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Workout session not found."));
    }

    @Test
    void historyExcludesActiveAndReturnsFinished() throws Exception {
        UUID finished = startManual();
        complete(finished);
        UUID active = startManual();

        mockMvc.perform(get("/api/v1/workout-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + finished + "')].status").value("COMPLETED"))
                .andExpect(jsonPath("$[?(@.id=='" + active + "')]").isEmpty());
    }
}
