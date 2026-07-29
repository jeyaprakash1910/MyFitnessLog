package com.myfitnesslog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * The owner of all application data. Version 1 is single-user: exactly one row
 * exists, seeded by V4__Seed_default_user.sql. Authentication is out of scope for
 * V1 (see PRD/DATABASE.md); this entity exists so Routine and WorkoutSession can
 * carry the required owner FK.
 *
 * Maps to the app_user table defined in V1__Initial_schema.sql. Audit timestamps
 * are inherited from AbstractAuditableEntity (ADR-0006).
 */
@Entity
@Table(name = "app_user")
public class User extends AbstractAuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        this.isDeleted = deleted;
    }
}
