package com.myfitnesslog.feature.exercise.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Local cache row for an exercise category.
 *
 * This entity is a download-only cache populated from the backend
 * `GET /exercise-categories` endpoint, whose response contract exposes only
 * `id` and `name` (see docs/API_SPECIFICATION.md). It therefore mirrors that
 * API contract rather than the full DATABASE.md schema: columns the API never
 * returns (displayOrder, createdAt, updatedAt, isDeleted) are intentionally
 * omitted because the app has no source of truth for them and does not use
 * them (KISS/YAGNI). The backend remains the source of truth (ADR-0003).
 *
 * Reference data carries no sync status because it is never uploaded — sync is
 * one-way, Android → backend, for user data only (SYNC.md, ANDROID_ARCHITECTURE.md).
 *
 * The table name is unquoted here because Android owns its own local SQLite
 * schema (Room); it must conform to the canonical model but is not the same
 * physical schema as PostgreSQL/Flyway.
 */
@Entity(tableName = "exercise_category")
data class ExerciseCategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "name")
    val name: String,
)
