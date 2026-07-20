package com.myfitnesslog.feature.routine.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.myfitnesslog.core.data.local.SyncStatus
import java.time.Instant
import java.util.UUID

/**
 * A workout template (routine). The first mutable, user-created entity.
 *
 * It carries auditing timestamps, a soft-delete flag, and a [SyncStatus] from
 * creation (the record is created locally as PENDING and uploaded later, in
 * Milestone 9). `userId` is not stored locally: Version 1 has a single user, and
 * the sync layer attaches the user id at upload time.
 */
@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Instant,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Instant,

    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
