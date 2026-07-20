package com.myfitnesslog.core.data.local.converters

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Stores [BigDecimal] values (weight, RPE, RIR) as their canonical String form.
 *
 * String storage preserves the exact value and scale, matching the backend's
 * DECIMAL columns and avoiding binary floating-point drift (the reason
 * BigDecimal was chosen over Double for workout measurements).
 */
class BigDecimalConverter {

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)
}
