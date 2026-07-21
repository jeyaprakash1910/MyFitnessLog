package com.myfitnesslog.entity;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.repository.RoutineRepository;
import com.myfitnesslog.support.JpaPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence round-trip for {@link Routine}: the owner FK to the seeded default
 * user, audit-timestamp population, and the soft-delete flag.
 */
@JpaPostgresTest
class RoutinePersistenceTest {

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private DefaultUserProvider defaultUserProvider;

    @Autowired
    private TestEntityManager entityManager;

    private Routine newRoutine(String name) {
        Routine routine = new Routine();
        routine.setId(UUID.randomUUID());
        routine.setUser(defaultUserProvider.getReference());
        routine.setName(name);
        routine.setDisplayOrder(0);
        return routine;
    }

    @Test
    void persistsAndReadsBackWithOwnerAndAuditTimestamps() {
        Routine saved = routineRepository.saveAndFlush(newRoutine("Push A"));
        entityManager.clear();

        Routine found = routineRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Push A");
        assertThat(found.getUser().getId()).isEqualTo(DefaultUserProvider.DEFAULT_USER_ID);
        assertThat(found.isDeleted()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void softDeleteFlagPersists() {
        Routine routine = newRoutine("Legs A");
        routine.setDeleted(true);
        Routine saved = routineRepository.saveAndFlush(routine);
        entityManager.clear();

        assertThat(routineRepository.findById(saved.getId()).orElseThrow().isDeleted()).isTrue();
    }
}
