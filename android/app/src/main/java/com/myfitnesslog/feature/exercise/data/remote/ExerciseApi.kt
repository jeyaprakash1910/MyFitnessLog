package com.myfitnesslog.feature.exercise.data.remote

import retrofit2.http.GET

/**
 * Retrofit interface for exercise endpoints.
 *
 * Only the list endpoint is declared. Search and by-id lookups are resolved
 * locally from Room, so those endpoints are intentionally not called here.
 */
interface ExerciseApi {

    @GET("exercises")
    suspend fun getExercises(): List<ExerciseDto>
}
