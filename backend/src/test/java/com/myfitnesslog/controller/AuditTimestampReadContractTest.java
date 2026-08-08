package com.myfitnesslog.controller;

import com.myfitnesslog.entity.Routine;
import com.myfitnesslog.repository.RoutineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read contract for {@code createdAt} and {@code updatedAt}.
 *
 * <p>Until this was added no read endpoint returned either, even though every row
 * has carried them since ADR-0006. Restore had to synthesise a timestamp, and
 * ADR-0017 Stage 3 could not be built at all, because last-write-wins has nothing
 * to compare.
 *
 * <p>Two properties matter more than mere presence, and both are asserted here:
 *
 * <ol>
 *   <li><b>{@code updatedAt} moves when the row changes.</b> A field that is
 *       present but frozen is worse than an absent one: last-write-wins would
 *       silently always pick the same side. Presence alone would not catch that.
 *   <li><b>The server assigns it, not the client.</b> ADR-0017 is explicit that
 *       device clocks drift and can be set by the user, so a device-authoritative
 *       timestamp would let a phone with a fast clock win every conflict forever.
 * </ol>
 *
 * <p>Reference data (exercises, categories) deliberately does not carry these
 * fields on read. The client never edits the catalogue, so it never participates
 * in conflict resolution, and adding them there would be surface with no caller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditTimestampReadContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineRepository routineRepository;

    private UUID createRoutine(String name) throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","name":"%s","description":"d","displayOrder":0}
                                """.formatted(id, name)))
                .andExpect(status().isCreated());
        return id;
    }

    private UUID startWorkout() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","startedAt":"2026-08-08T06:00:00Z"}
                                """.formatted(id)))
                .andExpect(status().isCreated());
        return id;
    }

    // ---------------------------------------------------------------- presence

    @Test
    void routineListAndDetailCarryBothTimestamps() throws Exception {
        UUID id = createRoutine("Push A");

        mockMvc.perform(get("/api/v1/routines/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/routines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].updatedAt").isNotEmpty());
    }

    @Test
    void workoutListAndDetailCarryBothTimestamps() throws Exception {
        UUID id = startWorkout();

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/workout-sessions"))
                .andExpect(status().isOk());
    }

    /**
     * The nested shapes matter as much as the top level: restore rebuilds a whole
     * graph from the detail endpoints, so a routine's exercises and a workout's
     * exercises and sets each need their own timestamps.
     */
    @Test
    void nestedExercisesAndSetsCarryTheirOwnTimestamps() throws Exception {
        UUID routineId = createRoutine("Legs");
        UUID exerciseId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID routineExerciseId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/routines/{id}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","exerciseId":"%s","exerciseOrder":0,"targetSets":3,
                                 "minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90}
                                """.formatted(routineExerciseId, exerciseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/routines/{id}", routineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.exercises[0].updatedAt").isNotEmpty());

        UUID sessionId = startWorkout();
        UUID workoutExerciseId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-sessions/{id}/exercises", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","exerciseId":"%s","exerciseName":"Barbell Bench Press",
                                 "exerciseOrder":0,"targetSets":3,"minTargetReps":8,"maxTargetReps":12}
                                """.formatted(workoutExerciseId, exerciseId)))
                .andExpect(status().isCreated());

        UUID setId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-exercises/{id}/sets", workoutExerciseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","setNumber":1,"weight":60.50,"repetitions":10,
                                 "setCategory":"WORKING","isCompleted":true}
                                """.formatted(setId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.exercises[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.exercises[0].sets[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.exercises[0].sets[0].updatedAt").isNotEmpty());
    }

    // ---------------------------------------------------------------- behaviour

    /**
     * A client cannot set its own timestamp. ADR-0017 requires the server's clock
     * to arbitrate, so an attempt to supply one must be ignored rather than
     * honoured.
     */
    @Test
    void aClientSuppliedTimestampIsIgnored() throws Exception {
        UUID id = UUID.randomUUID();
        Instant absurd = Instant.parse("2099-01-01T00:00:00Z");

        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","name":"Clock skew","description":"d","displayOrder":0,
                                 "createdAt":"%s","updatedAt":"%s"}
                                """.formatted(id, absurd, absurd)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/routines/{id}", id))
                .andExpect(status().isOk()).andReturn();

        assertThat(readInstant(result, "updatedAt"))
                .as("the server's clock decides, not the caller's")
                .isBefore(absurd);
    }

    /**
     * Persisted values, not just serialised ones. Confirms the field is genuinely
     * populated by JPA auditing rather than defaulted somewhere in the mapping.
     */
    @Test
    void theTimestampsComeFromTheStoredRow() throws Exception {
        UUID id = createRoutine("Stored");

        MvcResult result = mockMvc.perform(get("/api/v1/routines/{id}", id))
                .andExpect(status().isOk()).andReturn();

        Routine stored = routineRepository.findById(id).orElseThrow();
        assertThat(readInstant(result, "updatedAt")).isEqualTo(stored.getUpdatedAt());
        assertThat(readInstant(result, "createdAt")).isEqualTo(stored.getCreatedAt());
    }

    /**
     * Reference data stays as it was. Recorded as a test so the omission reads as
     * a decision rather than an oversight to whoever adds the next endpoint.
     */
    @Test
    void catalogueEndpointsDoNotExposeTimestamps() throws Exception {
        mockMvc.perform(get("/api/v1/exercise-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());

        mockMvc.perform(get("/api/v1/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    private Instant readInstant(MvcResult result, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        String value = com.jayway.jsonpath.JsonPath.read(json, "$." + field);
        return Instant.parse(value);
    }
}
