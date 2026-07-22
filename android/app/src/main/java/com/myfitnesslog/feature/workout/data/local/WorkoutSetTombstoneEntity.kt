package com.myfitnesslog.feature.workout.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A record that a [WorkoutSetEntity] was deleted, kept until the backend has
 * been told.
 *
 * ## Why this exists
 *
 * Every other mutable entity is soft-deleted, so the synchronization engine can
 * still see the row and propagate the deletion. A workout set is hard-deleted:
 * once it is gone there is nothing left to upload, and the backend keeps a set
 * the phone no longer has. That was invisible while Android was the only client;
 * the web client would render it (ADR-0007).
 *
 * ## Why a tombstone rather than a soft-delete flag
 *
 * A flag would be cheaper to add but would put `isDeleted = 0` into every
 * history read path forever, and history correctness is this project's highest
 * priority — a filter that one query forgets silently shows deleted sets. A
 * tombstone keeps the cost entirely inside the sync path, and rows leave the
 * database once uploaded rather than accumulating.
 *
 * ## Shape
 *
 * The primary key is the deleted set's own id, which makes recording a deletion
 * naturally idempotent: deleting the same set twice cannot queue it twice.
 *
 * `workoutSessionId` is denormalized rather than resolved through a join,
 * because the tombstone must survive independently of the live graph — the
 * exercise it belonged to may itself be gone by the time the upload runs. For
 * the same reason there is deliberately **no foreign key**: this is an outbox
 * row, not part of the workout snapshot, and a cascade must never silently
 * discard a pending deletion.
 *
 * There is no `syncStatus` column. A tombstone is the pending work; a successful
 * upload deletes the row, so its existence is the queue.
 */
@Entity(
    tableName = "workout_set_tombstone",
    indices = [Index(value = ["workoutSessionId"])],
)
data class WorkoutSetTombstoneEntity(
    /** The id of the deleted set — the path segment the DELETE request uses. */
    @PrimaryKey
    @ColumnInfo(name = "workoutSetId")
    val workoutSetId: UUID,

    @ColumnInfo(name = "workoutExerciseId")
    val workoutExerciseId: UUID,

    /** Denormalized: the engine skips deletions whose session failed to upload. */
    @ColumnInfo(name = "workoutSessionId")
    val workoutSessionId: UUID,

    @ColumnInfo(name = "deletedAt")
    val deletedAt: Instant,
)
