package com.myfitnesslog.feature.routine.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit interface for the routine upload endpoints (routines and their
 * exercises), grouped to match the backend's RoutineController and
 * RoutineExerciseController.
 *
 * Every call returns [Response] rather than a bare body so the synchronization
 * engine (Milestone 9 Phase 2) can distinguish 201 Created from 200 OK — the
 * backend's idempotent-save signal — and inspect failures without exceptions.
 * This interface performs no synchronization on its own; it is transport only.
 */
interface RoutineApi {

    @POST("routines")
    suspend fun createRoutine(
        @Body request: CreateRoutineRequestDto,
    ): Response<RoutineResponseDto>

    @PUT("routines/{id}")
    suspend fun updateRoutine(
        @Path("id") id: String,
        @Body request: UpdateRoutineRequestDto,
    ): Response<RoutineResponseDto>

    @DELETE("routines/{id}")
    suspend fun deleteRoutine(
        @Path("id") id: String,
    ): Response<Unit>

    @POST("routines/{routineId}/exercises")
    suspend fun addRoutineExercise(
        @Path("routineId") routineId: String,
        @Body request: AddRoutineExerciseRequestDto,
    ): Response<RoutineExerciseResponseDto>

    @PUT("routine-exercises/{id}")
    suspend fun updateRoutineExercise(
        @Path("id") id: String,
        @Body request: UpdateRoutineExerciseRequestDto,
    ): Response<RoutineExerciseResponseDto>

    @DELETE("routine-exercises/{id}")
    suspend fun deleteRoutineExercise(
        @Path("id") id: String,
    ): Response<Unit>
}
