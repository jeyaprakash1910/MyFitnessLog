package com.myfitnesslog.feature.exercise.data.remote

import retrofit2.http.GET

/**
 * Retrofit interface for exercise-category endpoints.
 *
 * Only the list endpoint is declared: it is the sole call needed to download the
 * reference data. Single-category lookups are served locally from Room, so no
 * by-id endpoint is exposed (docs/API_SPECIFICATION.md; YAGNI).
 */
interface ExerciseCategoryApi {

    @GET("exercise-categories")
    suspend fun getCategories(): List<ExerciseCategoryDto>
}
