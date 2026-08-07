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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read path for routines: {@code GET /api/v1/routines/{id}} must return enough
 * to reconstruct the routine, not merely identify it (ADR-0017).
 *
 * <p>This exists because the gap it covers was invisible for a long time. Every
 * routine endpoint was exercised by tests, all of them passed, and yet no endpoint
 * anywhere returned a routine's exercises: routine exercises could be created,
 * updated, reordered and deleted, but never read. A client could therefore write a
 * routine to the backend and be permanently unable to get it back. Tests that only
 * assert what an endpoint returns cannot catch a missing endpoint, which is why
 * this one is written from the caller's goal ("rebuild this routine") rather than
 * from the handler's signature.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoutineDetailReadTest {

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
                new CreateRoutineRequest(UUID.randomUUID(), "Pull B", "back day", 2)).routine().getId();
        exerciseId = exerciseRepository.findAll().get(0).getId();
    }

    private void addExercise(int order, int targetSets) throws Exception {
        mockMvc.perform(post("/api/v1/routines/{routineId}/exercises", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","exerciseId":"%s","exerciseOrder":%d,"targetSets":%d,
                                 "minTargetReps":8,"maxTargetReps":12,"targetRestSeconds":90,"notes":"n"}
                                """.formatted(UUID.randomUUID(), exerciseId, order, targetSets)))
                .andExpect(status().isCreated());
    }

    @Test
    void routineDetailCarriesEverythingNeededToRebuildIt() throws Exception {
        addExercise(0, 3);
        addExercise(1, 4);

        mockMvc.perform(get("/api/v1/routines/{id}", routineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(routineId.toString()))
                .andExpect(jsonPath("$.name").value("Pull B"))
                .andExpect(jsonPath("$.exercises.length()").value(2))
                // Every field a client needs to recreate the row locally.
                .andExpect(jsonPath("$.exercises[0].exerciseId").value(exerciseId.toString()))
                .andExpect(jsonPath("$.exercises[0].targetSets").value(3))
                .andExpect(jsonPath("$.exercises[0].minTargetReps").value(8))
                .andExpect(jsonPath("$.exercises[0].maxTargetReps").value(12))
                .andExpect(jsonPath("$.exercises[0].targetRestSeconds").value(90));
    }

    @Test
    void exercisesComeBackInPerformanceOrder() throws Exception {
        // Inserted back to front. The order is the routine, not a display
        // preference, so it has to survive the round trip rather than depend on
        // insertion order or on the caller sorting.
        addExercise(2, 5);
        addExercise(0, 3);
        addExercise(1, 4);

        mockMvc.perform(get("/api/v1/routines/{id}", routineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].exerciseOrder").value(0))
                .andExpect(jsonPath("$.exercises[1].exerciseOrder").value(1))
                .andExpect(jsonPath("$.exercises[2].exerciseOrder").value(2))
                .andExpect(jsonPath("$.exercises[0].targetSets").value(3))
                .andExpect(jsonPath("$.exercises[2].targetSets").value(5));
    }

    @Test
    void anEmptyRoutineReturnsAnEmptyListRatherThanNull() throws Exception {
        // A client iterating the list must not have to null-check it.
        mockMvc.perform(get("/api/v1/routines/{id}", routineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises").isArray())
                .andExpect(jsonPath("$.exercises.length()").value(0));
    }

    @Test
    void anUnknownRoutineIs404NotAnEmptyRoutine() throws Exception {
        // The distinction matters to a client rebuilding local state: treating
        // "does not exist" as "exists but is empty" would silently manufacture a
        // routine that was deleted.
        mockMvc.perform(get("/api/v1/routines/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theListEndpointStaysASummary() throws Exception {
        addExercise(0, 3);

        // Deliberately unchanged: a picker needs names, not every exercise of every
        // routine. Keeping the list lean is what makes the detail call worth having.
        mockMvc.perform(get("/api/v1/routines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + routineId + "')].exercises").isEmpty());
    }
}
