package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.service.WorkoutSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for {@link WorkoutExerciseController} over the full stack.
 * Transactional: each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutExerciseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutSessionService sessionService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private UUID sessionId;
    private UUID exerciseId;

    @BeforeEach
    void setUp() {
        sessionId = sessionService.startWorkout(
                new StartWorkoutSessionRequest(UUID.randomUUID(), null, null, Instant.parse("2026-07-20T09:00:00Z"), null))
                .session().getId();
        exerciseId = exerciseRepository.findAll().get(0).getId();
    }

    private String addBody(UUID id, int order, int targetSets, int minReps, int maxReps) {
        return """
                {"id":"%s","exerciseId":"%s","exerciseName":"Squat","exerciseOrder":%d,"targetSets":%d,"minTargetReps":%d,"maxTargetReps":%d,"targetRestSeconds":90,"notes":"n"}
                """.formatted(id, exerciseId, order, targetSets, minReps, maxReps);
    }

    private UUID addExercise() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/exercises", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, 0, 3, 8, 12)))
                .andExpect(status().isCreated());
        return id;
    }

    @Test
    void addReturns201ThenReplayReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/exercises", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, 0, 3, 8, 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exerciseName").value("Squat"))
                .andExpect(jsonPath("$.workoutSessionId").value(sessionId.toString()));

        mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/exercises", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, 0, 5, 8, 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetSets").value(5));
    }

    @Test
    void invalidTargetsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/exercises", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 0, 3, 12, 8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("maxTargetReps")));
    }

    @Test
    void unknownSessionAndExerciseReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/exercises", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 0, 3, 8, 12)))
                .andExpect(status().isNotFound());

        String unknownExercise = """
                {"id":"%s","exerciseId":"%s","exerciseName":"X","exerciseOrder":0,"targetSets":3,"minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90,"notes":null}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/exercises", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownExercise))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Exercise not found."));
    }

    @Test
    void updateReturns200AndMissingReturns404() throws Exception {
        UUID id = addExercise();
        mockMvc.perform(put("/api/v1/workout-exercises/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseName":"Front Squat","exerciseOrder":1,"targetSets":5,"minTargetReps":6,"maxTargetReps":10,"targetRestSeconds":60,"notes":"x"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseName").value("Front Squat"));

        mockMvc.perform(put("/api/v1/workout-exercises/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseName":"X","exerciseOrder":0,"targetSets":3,"minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90,"notes":null}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = addExercise();
        mockMvc.perform(delete("/api/v1/workout-exercises/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void addToCompletedSessionReturns409() throws Exception {
        addExercise();
        sessionService.completeWorkout(sessionId,
                new com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest(Instant.parse("2026-07-20T10:00:00Z"), null));

        mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/exercises", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 1, 3, 8, 12)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
