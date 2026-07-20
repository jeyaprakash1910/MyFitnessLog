package com.myfitnesslog.core.data.local.converters

import androidx.room.TypeConverter
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus

/**
 * Stores workout enums by name (stable if the enum order changes), matching the
 * [SyncStatusConverter] approach.
 */
class WorkoutEnumConverters {

    @TypeConverter
    fun fromWorkoutStatus(status: WorkoutStatus?): String? = status?.name

    @TypeConverter
    fun toWorkoutStatus(value: String?): WorkoutStatus? = value?.let(WorkoutStatus::valueOf)

    @TypeConverter
    fun fromSetCategory(category: SetCategory?): String? = category?.name

    @TypeConverter
    fun toSetCategory(value: String?): SetCategory? = value?.let(SetCategory::valueOf)
}
