package com.myfitnesslog.feature.workout.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for the workout **session lifecycle** endpoints.
 *
 * Kept separate from [WorkoutLogApi] because the two are used at different points
 * in the upload sequence: the session is created first and its terminal
 * transition is sent last, after every child has been uploaded (SYNC.md §8). That
 * ordering is the engine's responsibility (Phase 2); this interface is transport
 * only.
 */
interface WorkoutSessionApi {

    @POST("workout-sessions")
    suspend fun startWorkoutSession(
        @Body request: StartWorkoutSessionRequestDto,
    ): Response<WorkoutSessionResponseDto>

    @PUT("workout-sessions/{id}/complete")
    suspend fun completeWorkoutSession(
        @Path("id") id: String,
        @Body request: CompleteWorkoutSessionRequestDto,
    ): Response<WorkoutSessionResponseDto>

    @PUT("workout-sessions/{id}/discard")
    suspend fun discardWorkoutSession(
        @Path("id") id: String,
        @Body request: DiscardWorkoutSessionRequestDto,
    ): Response<WorkoutSessionResponseDto>
}
