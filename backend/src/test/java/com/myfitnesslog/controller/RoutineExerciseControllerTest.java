package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.CreateRoutineRequest;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.service.RoutineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for {@link RoutineExerciseController} over the full stack.
 * Transactional: each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoutineExerciseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private UUID routineId;
    private UUID exerciseId;

    @BeforeEach
    void setUp() {
        routineId = routineService.createRoutine(
                new CreateRoutineRequest(UUID.randomUUID(), "Legs", null, 0)).routine().getId();
        exerciseId = exerciseRepository.findAll().get(0).getId();
    }

    private String addBody(UUID id, int order, int targetSets, int minReps, int maxReps) {
        return """
                {"id":"%s","exerciseId":"%s","exerciseOrder":%d,"targetSets":%d,"minTargetReps":%d,"maxTargetReps":%d,"targetRestSeconds":90,"notes":"n"}
                """.formatted(id, exerciseId, order, targetSets, minReps, maxReps);
    }

    private UUID addExercise(int order) throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/routines/{routineId}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, order, 3, 8, 12)))
                .andExpect(status().isCreated());
        return id;
    }

    @Test
    void addReturns201ThenReplayReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/routines/{routineId}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, 0, 3, 8, 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exerciseId").value(exerciseId.toString()))
                .andExpect(jsonPath("$.targetSets").value(3));

        mockMvc.perform(post("/api/v1/routines/{routineId}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(id, 0, 5, 8, 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetSets").value(5));
    }

    @Test
    void addRejectsInvalidTargets() throws Exception {
        // targetSets = 0 → @Positive fails
        mockMvc.perform(post("/api/v1/routines/{routineId}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 0, 0, 8, 12)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // maxTargetReps < minTargetReps → @AssertTrue fails
        mockMvc.perform(post("/api/v1/routines/{routineId}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 0, 3, 12, 8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("maxTargetReps")));
    }

    @Test
    void addRejectsUnknownRoutineAndExercise() throws Exception {
        mockMvc.perform(post("/api/v1/routines/{routineId}/exercises", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(UUID.randomUUID(), 0, 3, 8, 12)))
                .andExpect(status().isNotFound());

        String unknownExercise = """
                {"id":"%s","exerciseId":"%s","exerciseOrder":0,"targetSets":3,"minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90,"notes":"n"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/routines/{routineId}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownExercise))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Exercise not found."));
    }

    @Test
    void updateReturns200AndMissingReturns404() throws Exception {
        UUID id = addExercise(0);

        mockMvc.perform(put("/api/v1/routine-exercises/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseOrder":2,"targetSets":5,"minTargetReps":6,"maxTargetReps":10,"targetRestSeconds":60,"notes":"x"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetSets").value(5))
                .andExpect(jsonPath("$.exerciseOrder").value(2));

        mockMvc.perform(put("/api/v1/routine-exercises/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseOrder":0,"targetSets":3,"minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90,"notes":null}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = addExercise(0);
        mockMvc.perform(delete("/api/v1/routine-exercises/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void reorderReturns200AndInvalidReturns409() throws Exception {
        UUID first = addExercise(0);
        UUID second = addExercise(1);

        mockMvc.perform(put("/api/v1/routines/{routineId}/exercise-order", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderedIds":["%s","%s"]}
                                """.formatted(second, first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[0].exerciseOrder").value(0))
                .andExpect(jsonPath("$[1].id").value(first.toString()));

        // Missing one id → business rule violation → 409
        mockMvc.perform(put("/api/v1/routines/{routineId}/exercise-order", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderedIds":["%s"]}
                                """.formatted(first)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
