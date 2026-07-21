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
 * One planned exercise within a routine, carrying the workout targets
 * (DATABASE.md). Belongs to a routine (FK cascades on routine delete) and
 * references a master Exercise (RESTRICT). No soft-delete column: it is a
 * template child removed with its routine or by explicit request.
 *
 * Maps to the "RoutineExercise" table in V1__Initial_schema.sql; audit
 * timestamps inherited from AbstractAuditableEntity.
 */
@Entity
@Table(name = "\"RoutineExercise\"")
public class RoutineExercise extends AbstractAuditableEntity {

    @Id
    @Column(name = "\"id\"", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "\"routineId\"", nullable = false)
    private Routine routine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "\"exerciseId\"", nullable = false)
    private Exercise exercise;

    @Column(name = "\"exerciseOrder\"", nullable = false)
    private int exerciseOrder;

    @Column(name = "\"targetSets\"", nullable = false)
    private int targetSets;

    @Column(name = "\"minTargetReps\"", nullable = false)
    private int minTargetReps;

    @Column(name = "\"maxTargetReps\"", nullable = false)
    private int maxTargetReps;

    @Column(name = "\"targetRestSeconds\"")
    private Integer targetRestSeconds;

    @Column(name = "\"notes\"")
    private String notes;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Routine getRoutine() {
        return routine;
    }

    public void setRoutine(Routine routine) {
        this.routine = routine;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
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
