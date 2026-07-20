package com.myfitnesslog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class providing framework-managed audit timestamps for all entities.
 * See ADR-0006. createdAt and updatedAt are populated exclusively by Spring
 * Data JPA Auditing and must never be assigned directly by domain entities.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditableEntity {

    @CreatedDate
    @Column(name = "\"createdAt\"", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "\"updatedAt\"", nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
