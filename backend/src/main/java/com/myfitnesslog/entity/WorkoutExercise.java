package com.myfitnesslog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A historical snapshot of one exercise performed during a workout (DATABASE.md).
 * exerciseName and the target fields are copied from the routine at workout start
 * so history stays accurate if the routine/exercise later changes. Belongs to a
 * session (FK cascades) and references a master Exercise (RESTRICT).
 *
 * Maps to the workout_exercise table in V1__Initial_schema.sql; audit timestamps
 * inherited from AbstractAuditableEntity.
 */
@Entity
@Table(name = "workout_exercise")
public class WorkoutExercise extends AbstractAuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_session_id", nullable = false)
    private WorkoutSession workoutSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(name = "exercise_name", nullable = false, length = 150)
    private String exerciseName;

    @Column(name = "exercise_order", nullable = false)
    private int exerciseOrder;

    @Column(name = "target_sets", nullable = false)
    private int targetSets;

    @Column(name = "min_target_reps", nullable = false)
    private int minTargetReps;

    @Column(name = "max_target_reps", nullable = false)
    private int maxTargetReps;

    @Column(name = "target_rest_seconds")
    private Integer targetRestSeconds;

    @Column(name = "notes")
    private String notes;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WorkoutSession getWorkoutSession() {
        return workoutSession;
    }

    public void setWorkoutSession(WorkoutSession workoutSession) {
        this.workoutSession = workoutSession;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName) {
        this.exerciseName = exerciseName;
    }

    public int getExerciseOrder() {
        return exerciseOrder;
    }

    public void setExerciseOrder(int exerciseOrder) {
        this.exerciseOrder = exerciseOrder;
    }

    public int getTargetSets() {
        return targetSets;
    }

    public void setTargetSets(int targetSets) {
        this.targetSets = targetSets;
    }

    public int getMinTargetReps() {
        return minTargetReps;
    }

    public void setMinTargetReps(int minTargetReps) {
        this.minTargetReps = minTargetReps;
    }

    public int getMaxTargetReps() {
        return maxTargetReps;
    }

    public void setMaxTargetReps(int maxTargetReps) {
        this.maxTargetReps = maxTargetReps;
    }

    public Integer getTargetRestSeconds() {
        return targetRestSeconds;
    }

    public void setTargetRestSeconds(Integer targetRestSeconds) {
        this.targetRestSeconds = targetRestSeconds;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
