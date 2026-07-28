package com.myfitnesslog.feature.workout.ui

import java.math.BigDecimal

/**
 * The canonical Version 2 RPE scale and its guidance copy (spec §4, DEC — the
 * 8-value scale `6, 7, 7.5, 8, 8.5, 9, 9.5, 10`).
 *
 * These are the only values the picker offers; the repository's numeric validation
 * (rpe in 1..10) remains the authoritative guard, so historical values outside this
 * set still render. The effort label/description are presentation copy for the sheet.
 */
object RpeScale {

    /** The allowed picker values, in ascending order. */
    val values: List<BigDecimal> = listOf("6", "7", "7.5", "8", "8.5", "9", "9.5", "10").map(::BigDecimal)

    data class Descriptor(val label: String, val hint: String)

    /** Effort label + plain-language hint for a value (empty for anything off-scale). */
    fun describe(value: BigDecimal): Descriptor = when (value.stripTrailingZeros().toPlainString()) {
        "6" -> Descriptor("Moderate Effort", "Could have done 4+ more reps")
        "7" -> Descriptor("Vigorous Effort", "Could have definitely done 3 more reps")
        "7.5" -> Descriptor("Vigorous Effort", "Could have maybe done 3 more reps")
        "8" -> Descriptor("Very Hard Effort", "Could have definitely done 2 more reps")
        "8.5" -> Descriptor("Very Hard Effort", "Could have maybe done 2 more reps")
        "9" -> Descriptor("Extremely Hard Effort", "Could have definitely done 1 more rep")
        "9.5" -> Descriptor("Extremely Hard Effort", "Could have maybe done 1 more rep")
        "1E+1", "10" -> Descriptor("Max Effort", "No more reps possible")
        else -> Descriptor("", "")
    }
}
