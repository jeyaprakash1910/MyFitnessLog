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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One performed set — the smallest unit of workout history (DATABASE.md). Stores
 * measured facts only; derived metrics (rest/set duration) are computed by
 * clients from timestamps and never stored. weight/rpe/rir use BigDecimal to
 * match the NUMERIC columns exactly. setCategory is persisted by name.
 *
 * Value guards (weight >= 0, rpe 1..10, etc.) are enforced by the service layer
 * and the database CHECK constraints. Maps to the "WorkoutSet" table in
 * V1__Initial_schema.sql; audit timestamps inherited from AbstractAuditableEntity.
 */
@Entity
@Table(name = "\"WorkoutSet\"")
public class WorkoutSet extends AbstractAuditableEntity {

    @Id
    @Column(name = "\"id\"", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "\"workoutExerciseId\"", nullable = false)
    private WorkoutExercise workoutExercise;

    @Column(name = "\"setNumber\"", nullable = false)
    private int setNumber;

    @Column(name = "\"weight\"", nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "\"repetitions\"", nullable = false)
    private int repetitions;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"setCategory\"", nullable = false, length = 20)
    private SetCategory setCategory = SetCategory.WORKING;

    @Column(name = "\"startedAt\"")
    private Instant startedAt;

    @Column(name = "\"finishedAt\"")
    private Instant finishedAt;

    @Column(name = "\"rpe\"", precision = 3, scale = 1)
    private BigDecimal rpe;

    @Column(name = "\"rir\"", precision = 3, scale = 1)
    private BigDecimal rir;

    @Column(name = "\"isCompleted\"", nullable = false)
    private boolean isCompleted = true;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WorkoutExercise getWorkoutExercise() {
        return workoutExercise;
    }

    public void setWorkoutExercise(WorkoutExercise workoutExercise) {
        this.workoutExercise = workoutExercise;
    }

    public int getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(int setNumber) {
        this.setNumber = setNumber;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }

    public SetCategory getSetCategory() {
        return setCategory;
    }

    public void setSetCategory(SetCategory setCategory) {
        this.setCategory = setCategory;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public BigDecimal getRpe() {
        return rpe;
    }

    public void setRpe(BigDecimal rpe) {
        this.rpe = rpe;
    }

    public BigDecimal getRir() {
        return rir;
    }

    public void setRir(BigDecimal rir) {
        this.rir = rir;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        this.isCompleted = completed;
    }
}
