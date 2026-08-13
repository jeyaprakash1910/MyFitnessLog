package com.myfitnesslog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One workout — a completed, in-progress, or discarded gym visit (DATABASE.md).
 * Part of workout history: never soft-deleted. A null routine denotes a manual
 * workout. status is persisted by name to match the CHECK constraint.
 *
 * Domain timestamps (startedAt, endedAt) are client-supplied and preserved
 * exactly; audit timestamps (createdAt, updatedAt) are backend metadata managed
 * by JPA auditing. Maps to the workout_session table in V1__Initial_schema.sql.
 */
@Entity
@Table(name = "workout_session")
public class WorkoutSession extends AbstractAuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private Routine routine;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkoutStatus status = WorkoutStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /**
     * The routine's name when the workout started, or null for a manual workout.
     *
     * Snapshotted rather than read through {@link #routine}, for the same reason
     * {@code WorkoutExercise} copies the exercise name: renaming a routine must not
     * relabel the workouts already performed from it (ADR-0004).
     */
    @Column(name = "routine_name", length = 100)
    private String routineName;

    @Column(name = "notes")
    private String notes;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Routine getRoutine() {
        return routine;
    }

    public String getRoutineName() {
        return routineName;
    }

    public void setRoutineName(String routineName) {
        this.routineName = routineName;
    }

    public void setRoutine(Routine routine) {
        this.routine = routine;
    }

    public WorkoutStatus getStatus() {
        return status;
    }

    public void setStatus(WorkoutStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
