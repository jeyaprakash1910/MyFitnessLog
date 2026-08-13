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

    private void discard(UUID id) throws Exception {
        mockMvc.perform(put("/api/v1/workout-sessions/{id}/discard", id)
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

    /**
     * The routine's name is recorded on the session, and does not follow a later
     * rename.
     *
     * Snapshotted for the same reason workout_exercise copies the exercise name: a
     * workout records what happened, and relabelling every past session because a
     * routine was renamed is the failure ADR-0004 exists to prevent. The client
     * sends the name it displayed, rather than the server resolving it, so the
     * record matches what the user was looking at.
     */
    @Test
    void startRecordsTheRoutineNameAndKeepsItAcrossAReplay() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","routineId":null,"routineName":"Push","startedAt":"%s","notes":null}
                                """.formatted(id, STARTED_AT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routineName").value("Push"));

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", id))
                .andExpect(jsonPath("$.routineName").value("Push"));
    }

    /** A manual workout has no routine, so it has no name to record. */
    @Test
    void aManualWorkoutHasNoRoutineName() throws Exception {
        UUID id = startManual();

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routineId").isEmpty())
                .andExpect(jsonPath("$.routineName").isEmpty());
    }

    /**
     * A completed workout can be discarded, which is how one logged by mistake
     * leaves the record.
     *
     * <p>This test asserted the opposite until 2026-08-13, when the transition was
     * illegal. It is rewritten rather than removed, because the 409 it expected was
     * a deliberate rule and its replacement is a deliberate decision, not a
     * loosening nobody noticed.
     */
    @Test
    void completedWorkoutCanBeDiscarded() throws Exception {
        UUID id = startManual();
        complete(id);

        discard(id);

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
    }

    /**
     * Discarding afterwards must not rewrite when training stopped.
     *
     * <p>endedAt is what every history screen derives duration from. Replacing it
     * with the moment somebody removed the workout would substitute a fact about the
     * removal for a fact about the workout, so the later timestamp sent below is
     * deliberately ignored.
     */
    @Test
    void discardingACompletedWorkoutPreservesTheOriginalEndedAt() throws Exception {
        UUID id = startManual();
        complete(id);

        mockMvc.perform(put("/api/v1/workout-sessions/{id}/discard", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"2026-08-13T23:59:00Z","notes":null}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").value(ENDED_AT));
    }

    /** A discarded workout leaves history, which is what discarding is for. */
    @Test
    void aDiscardedWorkoutLeavesHistory() throws Exception {
        UUID kept = startManual();
        complete(kept);
        UUID removed = startManual();
        complete(removed);

        mockMvc.perform(get("/api/v1/workout-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + removed + "')]").exists());

        discard(removed);

        mockMvc.perform(get("/api/v1/workout-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + removed + "')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id=='" + kept + "')]").exists());
    }

    /** Discarding twice is a no-op, which synchronisation replay depends on. */
    @Test
    void discardReplayIsIdempotent() throws Exception {
        UUID id = startManual();
        complete(id);
        discard(id);

        discard(id);

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.endedAt").value(ENDED_AT));
    }

    /**
     * DISCARDED stays terminal. Un-discarding is possible in the data but is not a
     * transition any client may perform, so the rule is enforced rather than left to
     * callers to respect.
     */
    @Test
    void aDiscardedWorkoutCannotBeCompleted() throws Exception {
        UUID id = startManual();
        discard(id);

        mockMvc.perform(put("/api/v1/workout-sessions/{id}/complete", id)
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
    void historyReturnsCompletedOnly() throws Exception {
        UUID finished = startManual();
        complete(finished);
        UUID active = startManual();
        UUID abandoned = startManual();
        discard(abandoned);

        mockMvc.perform(get("/api/v1/workout-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + finished + "')].status").value("COMPLETED"))
                .andExpect(jsonPath("$[?(@.id=='" + active + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.id=='" + abandoned + "')]").isEmpty());
    }
}
