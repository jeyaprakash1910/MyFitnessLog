package com.myfitnesslog.feature.history.data.local

import androidx.room.ColumnInfo
import java.math.BigDecimal

/**
 * A single set from a previous completed workout (V2 Milestone E — read-only
 * projection). Populated by [WorkoutHistoryDao.getPreviousSets]; carries only what
 * the PREVIOUS column needs. Not an entity — never persisted.
 *
 * See docs/internal/V2/V2_PREVIOUS_PERFORMANCE_CONTRACT.md.
 */
data class PreviousSetPerformance(
    @ColumnInfo(name = "setNumber") val setNumber: Int,
    @ColumnInfo(name = "weight") val weight: BigDecimal,
    @ColumnInfo(name = "repetitions") val repetitions: Int,
    @ColumnInfo(name = "rpe") val rpe: BigDecimal?,
)
