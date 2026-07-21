package com.myfitnesslog.feature.workout.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit interface for the workout **contents** endpoints — the exercises and
 * sets logged inside a session, matching the backend's WorkoutExerciseController
 * and WorkoutSetController.
 *
 * The backend permits these calls only while the parent session is IN_PROGRESS,
 * which is why the session's terminal transition ([WorkoutSessionApi]) must be
 * uploaded after everything here.
 */
interface WorkoutLogApi {

    @POST("workout-sessions/{sessionId}/exercises")
    suspend fun addWorkoutExercise(
        @Path("sessionId") sessionId: String,
        @Body request: AddWorkoutExerciseRequestDto,
    ): Response<WorkoutExerciseResponseDto>

    @PUT("workout-exercises/{id}")
    suspend fun updateWorkoutExercise(
        @Path("id") id: String,
        @Body request: UpdateWorkoutExerciseRequestDto,
    ): Response<WorkoutExerciseResponseDto>

    @DELETE("workout-exercises/{id}")
    suspend fun deleteWorkoutExercise(
        @Path("id") id: String,
    ): Response<Unit>

    @POST("workout-exercises/{workoutExerciseId}/sets")
    suspend fun addWorkoutSet(
        @Path("workoutExerciseId") workoutExerciseId: String,
        @Body request: AddWorkoutSetRequestDto,
    ): Response<WorkoutSetResponseDto>

    @PUT("workout-sets/{id}")
    suspend fun updateWorkoutSet(
        @Path("id") id: String,
        @Body request: UpdateWorkoutSetRequestDto,
    ): Response<WorkoutSetResponseDto>

    @DELETE("workout-sets/{id}")
    suspend fun deleteWorkoutSet(
        @Path("id") id: String,
    ): Response<Unit>
}
