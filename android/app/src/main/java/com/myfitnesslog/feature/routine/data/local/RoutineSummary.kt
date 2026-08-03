package com.myfitnesslog.feature.routine.data.local

import java.util.UUID

/**
 * Lightweight projection of a routine plus its exercise count, used to render
 * the Home routine list without loading each routine's exercises. Populated by
 * [RoutineDao.observeSummaries].
 */
data class RoutineSummary(
    val id: UUID,
    val name: String,
    val exerciseCount: Int,
)
