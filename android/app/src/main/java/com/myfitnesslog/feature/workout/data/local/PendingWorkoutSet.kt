package com.myfitnesslog.feature.workout.data.local

import androidx.room.Embedded
import java.util.UUID

/**
 * A set awaiting upload, together with the id of the session it ultimately
 * belongs to.
 *
 * A [WorkoutSetEntity] references only its parent workout exercise, but the
 * synchronization engine works in whole aggregates: if a session (or one of its
 * exercises) failed to upload, that session's sets must be skipped rather than
 * POSTed against a parent the backend has never seen. Carrying the resolved
 * session id alongside the row keeps that decision a pure in-memory check.
 *
 * A query projection, not an entity — it has no table of its own.
 */
data class PendingWorkoutSet(
    @Embedded val set: WorkoutSetEntity,
    val workoutSessionId: UUID,
)
