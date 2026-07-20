package com.myfitnesslog.core.data.local.converters

import androidx.room.TypeConverter
import com.myfitnesslog.core.data.local.SyncStatus

/**
 * Stores [SyncStatus] as its enum name. Storing the name (rather than the
 * ordinal) keeps the persisted value stable if the enum order ever changes.
 */
class SyncStatusConverter {

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String? = status?.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? = value?.let(SyncStatus::valueOf)
}
