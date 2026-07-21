package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.AddWorkoutExerciseRequest;
import com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest;
import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.service.WorkoutExerciseService;
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
 * HTTP-level tests for {@link WorkoutSetController} over the full stack.
 * Transactional: each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutSetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutSessionService sessionService;

    @Autowired
    private WorkoutExerciseService exerciseService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private UUID sessionId;
    private UUID workoutExerciseId;

    @BeforeEach
    void setUp() {
        sessionId = sessionService.startWorkout(
                new StartWorkoutSessionRequest(UUID.randomUUID(), null, Instant.parse("2026-07-20T09:00:00Z"), null))
                .session().getId();
        UUID exerciseId = exerciseRepository.findAll().get(0).getId();
        workoutExerciseId = exerciseService.addExercise(sessionId,
                new AddWorkoutExerciseRequest(UUID.randomUUID(), exerciseId, "Squat", 0, 3, 8, 12, 90, null))
                .workoutExercise().getId();
    }

    private String addBody(UUID id, int setNumber, String weight, String rpe) {
        return """
                {"id":"%s","setNumber":%d,"weight":%s,"repetitions":5,"setCategory":"WORKING","startedAt":null,"finishedAt":null,"rpe":%s,"rir":null,"isCompleted":true}
                """.formatted(id, setNumber, weight, rpe);
    }

    private UUID addSet(int setNumber) throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-exercises/{id}/sets", workoutExerciseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, setNumber, "100.00", "8.5")))
                .andExpect(status().isCreated());
        return id;
    }

    @Test
    void addReturns201ThenReplayReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workout-exercises/{id}/sets", workoutExerciseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, 1, "100.00", "8.5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weight").value(100.00))
                .andExpect(jsonPath("$.setCategory").value("WORKING"))
                .andExpect(jsonPath("$.workoutExerciseId").value(workoutExerciseId.toString()));

        mockMvc.perform(post("/api/v1/workout-exercises/{id}/sets", workoutExerciseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, 1, "110.00", "9.0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(110.00));
    }

    @Test
    void rpeOutOfRangeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/workout-exercises/{id}/sets", workoutExerciseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 1, "100.00", "20.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("rpe")));
    }

    @Test
    void unknownExerciseReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/workout-exercises/{id}/sets", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 1, "100.00", "8.0")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Workout exercise not found."));
    }

    @Test
    void updateAndDelete() throws Exception {
        UUID id = addSet(1);
        mockMvc.perform(put("/api/v1/workout-sets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"setNumber":1,"weight":120.00,"repetitions":3,"setCategory":"TOP_SET","startedAt":null,"finishedAt":null,"rpe":9.5,"rir":null,"isCompleted":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setCategory").value("TOP_SET"))
                .andExpect(jsonPath("$.isCompleted").value(false));

        mockMvc.perform(delete("/api/v1/workout-sets/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void mutationOnCompletedSessionReturns409() throws Exception {
        addSet(1);
        sessionService.completeWorkout(sessionId,
                new CompleteWorkoutSessionRequest(Instant.parse("2026-07-20T10:00:00Z"), null));

        mockMvc.perform(post("/api/v1/workout-exercises/{id}/sets", workoutExerciseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 2, "100.00", "8.0")))
                .andExpect(status().isConflict());
    }
}
