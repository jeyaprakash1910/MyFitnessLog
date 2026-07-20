package com.myfitnesslog.core.data.local.converters

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Converts [Instant] timestamps to and from epoch milliseconds for storage.
 *
 * All timestamps are stored in UTC with millisecond precision
 * (docs/DATABASE.md, Timestamp Strategy). [Instant] is inherently UTC and is
 * available natively from API 26, which is the app's minSdk — so no core
 * library desugaring is required.
 */
class InstantConverter {

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? = epochMillis?.let(Instant::ofEpochMilli)
}
