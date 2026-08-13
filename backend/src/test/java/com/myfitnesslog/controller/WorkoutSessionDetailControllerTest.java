package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.AddWorkoutExerciseRequest;
import com.myfitnesslog.dto.request.AddWorkoutSetRequest;
import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.entity.SetCategory;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.service.WorkoutExerciseService;
import com.myfitnesslog.service.WorkoutSessionService;
import com.myfitnesslog.service.WorkoutSetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies GET /workout-sessions/{id} returns the complete nested snapshot
 * (session → exercises → sets) with the original exercise and set ordering.
 * Transactional: each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutSessionDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutSessionService sessionService;

    @Autowired
    private WorkoutExerciseService exerciseService;

    @Autowired
    private WorkoutSetService setService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Test
    void detailReturnsNestedSnapshotInOrder() throws Exception {
        UUID sessionId = sessionService.startWorkout(
                new StartWorkoutSessionRequest(UUID.randomUUID(), null, null, Instant.parse("2026-07-20T09:00:00Z"), "manual"))
                .session().getId();
        UUID exerciseId = exerciseRepository.findAll().get(0).getId();

        // Two exercises added out of order (order 1 then 0) to prove ordering.
        UUID second = exerciseService.addExercise(sessionId,
                new AddWorkoutExerciseRequest(UUID.randomUUID(), exerciseId, "Bench", 1, 3, 8, 12, 90, null))
                .workoutExercise().getId();
        UUID first = exerciseService.addExercise(sessionId,
                new AddWorkoutExerciseRequest(UUID.randomUUID(), exerciseId, "Squat", 0, 3, 8, 12, 90, null))
                .workoutExercise().getId();

        // Two sets on the first exercise, inserted out of order.
        setService.addSet(first, new AddWorkoutSetRequest(UUID.randomUUID(), 2, new BigDecimal("110.00"), 3,
                SetCategory.TOP_SET, null, null, null, null, true));
        setService.addSet(first, new AddWorkoutSetRequest(UUID.randomUUID(), 1, new BigDecimal("100.00"), 5,
                SetCategory.WORKING, null, null, new BigDecimal("8.5"), null, true));

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.routineId").isEmpty())
                .andExpect(jsonPath("$.notes").value("manual"))
                // exercises ordered by exerciseOrder: Squat (0) then Bench (1)
                .andExpect(jsonPath("$.exercises[0].id").value(first.toString()))
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("Squat"))
                .andExpect(jsonPath("$.exercises[1].id").value(second.toString()))
                .andExpect(jsonPath("$.exercises[1].exerciseName").value("Bench"))
                // sets on the first exercise ordered by setNumber
                .andExpect(jsonPath("$.exercises[0].sets[0].setNumber").value(1))
                .andExpect(jsonPath("$.exercises[0].sets[0].weight").value(100.00))
                .andExpect(jsonPath("$.exercises[0].sets[0].rpe").value(8.5))
                .andExpect(jsonPath("$.exercises[0].sets[1].setNumber").value(2))
                .andExpect(jsonPath("$.exercises[1].sets").isEmpty());
    }
}
