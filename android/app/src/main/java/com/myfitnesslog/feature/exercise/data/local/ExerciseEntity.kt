package com.myfitnesslog.feature.exercise.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Local cache row for a master exercise.
 *
 * Populated from the backend `GET /exercises` endpoint. It mirrors the
 * `ExerciseResponse` contract (id, categoryId, name, description, instructions,
 * equipment) — the only fields the API provides — rather than the full
 * DATABASE.md schema (createdAt/updatedAt/isDeleted are omitted for the same
 * reason as [ExerciseCategoryEntity]).
 *
 * A foreign key to [ExerciseCategoryEntity] with `RESTRICT` mirrors the backend
 * referential rule (docs/DATABASE.md: ExerciseCategory → Exercise is ON DELETE
 * RESTRICT) and guards against orphaned rows in the local cache. Categories are
 * therefore downloaded before exercises.
 */
@Entity(
    tableName = "exercise",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["categoryId"])],
)
data class ExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "categoryId")
    val categoryId: UUID,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "instructions")
    val instructions: String? = null,

    @ColumnInfo(name = "equipment")
    val equipment: String? = null,
)
