package com.myfitnesslog.integration;

import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.repository.RoutineExerciseRepository;
import com.myfitnesslog.repository.RoutineRepository;
import com.myfitnesslog.repository.WorkoutExerciseRepository;
import com.myfitnesslog.repository.WorkoutSessionRepository;
import com.myfitnesslog.repository.WorkoutSetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The primary synchronization proof for Milestone 8. Submits the entire workout
 * graph in dependency order (Routine → RoutineExercise → WorkoutSession →
 * WorkoutExercise → WorkoutSet), then submits the exact same graph again with the
 * same client UUIDs, asserting: 201 on first pass, 200 on replay, no duplicate
 * rows, and an identical final state. Transactional: rolls back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SyncGraphIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private RoutineRepository routineRepository;
    @Autowired
    private RoutineExerciseRepository routineExerciseRepository;
    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;
    @Autowired
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Autowired
    private WorkoutSetRepository workoutSetRepository;

    private final UUID routineId = UUID.randomUUID();
    private final UUID routineExerciseId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID workoutExerciseId = UUID.randomUUID();
    private final UUID workoutSetId = UUID.randomUUID();

    private UUID exerciseId() {
        return exerciseRepository.findAll().get(0).getId();
    }

    /** Submits the full graph in dependency order, asserting the given status on each POST. */
    private void submitGraph(ResultMatcher expectedStatus) throws Exception {
        UUID exerciseId = exerciseId();

        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","name":"Push A","description":"d","displayOrder":0}
                                """.formatted(routineId)))
                .andExpect(expectedStatus);

        mockMvc.perform(post("/api/v1/routines/{id}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","exerciseId":"%s","exerciseOrder":0,"targetSets":3,"minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90,"notes":"n"}
                                """.formatted(routineExerciseId, exerciseId)))
                .andExpect(expectedStatus);

        mockMvc.perform(post("/api/v1/workout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","routineId":"%s","startedAt":"2026-07-20T09:00:00Z","notes":"w"}
                                """.formatted(sessionId, routineId)))
                .andExpect(expectedStatus);

        mockMvc.perform(post("/api/v1/workout-sessions/{id}/exercises", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","exerciseId":"%s","exerciseName":"Squat","exerciseOrder":0,"targetSets":3,"minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90,"notes":"n"}
                                """.formatted(workoutExerciseId, exerciseId)))
                .andExpect(expectedStatus);

        mockMvc.perform(post("/api/v1/workout-exercises/{id}/sets", workoutExerciseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","setNumber":1,"weight":100.00,"repetitions":5,"setCategory":"WORKING","startedAt":null,"finishedAt":null,"rpe":8.5,"rir":null,"isCompleted":true}
                                """.formatted(workoutSetId)))
                .andExpect(expectedStatus);
    }

    @Test
    void fullGraphSubmitThenIdempotentReplay() throws Exception {
        // First pass: everything is created.
        submitGraph(status().isCreated());
        assertCounts();

        // Replay with identical UUIDs: everything converges, nothing duplicates.
        submitGraph(status().isOk());
        assertCounts();

        // Final state is exactly one of each row, with the submitted values.
        assertThat(workoutSetRepository.findById(workoutSetId).orElseThrow().getWeight())
                .isEqualByComparingTo("100.00");
        assertThat(workoutExerciseRepository.findById(workoutExerciseId).orElseThrow().getExerciseName())
                .isEqualTo("Squat");
        assertThat(workoutSessionRepository.findById(sessionId).orElseThrow().getRoutine().getId())
                .isEqualTo(routineId);
    }

    private void assertCounts() {
        assertThat(routineRepository.count()).isEqualTo(1);
        assertThat(routineExerciseRepository.count()).isEqualTo(1);
        assertThat(workoutSessionRepository.count()).isEqualTo(1);
        assertThat(workoutExerciseRepository.count()).isEqualTo(1);
        assertThat(workoutSetRepository.count()).isEqualTo(1);
    }

    @Test
    void detailReflectsSyncedRoutineWorkoutAfterCompletion() throws Exception {
        submitGraph(status().isCreated());

        mockMvc.perform(put("/api/v1/workout-sessions/{id}/complete", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"2026-07-20T10:00:00Z","notes":null}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.routineId").value(routineId.toString()))
                .andExpect(jsonPath("$.endedAt").value("2026-07-20T10:00:00Z"))
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("Squat"))
                .andExpect(jsonPath("$.exercises[0].sets[0].setNumber").value(1))
                .andExpect(jsonPath("$.exercises[0].sets[0].weight").value(100.00));

        // History includes the completed workout.
        mockMvc.perform(get("/api/v1/workout-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + sessionId + "')].status").value("COMPLETED"));
    }
}
