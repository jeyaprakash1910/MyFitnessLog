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
 * Master data entity representing a reusable exercise in the library.
 * Maps to the "Exercise" table defined in V1__Initial_schema.sql.
 * Audit timestamps are inherited from AbstractAuditableEntity (see ADR-0006).
 */
@Entity
@Table(name = "\"Exercise\"")
public class Exercise extends AbstractAuditableEntity {

    @Id
    @Column(name = "\"id\"", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "\"categoryId\"", nullable = false)
    private ExerciseCategory category;

    @Column(name = "\"name\"", nullable = false, length = 150)
    private String name;

    @Column(name = "\"description\"")
    private String description;

    @Column(name = "\"instructions\"")
    private String instructions;

    @Column(name = "\"equipment\"", length = 100)
    private String equipment;

    @Column(name = "\"isDeleted\"", nullable = false)
    private boolean isDeleted = false;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ExerciseCategory getCategory() {
        return category;
    }

    public void setCategory(ExerciseCategory category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        this.isDeleted = deleted;
    }
}
