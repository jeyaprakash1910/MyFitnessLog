package com.myfitnesslog.entity;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.repository.WorkoutSessionRepository;
import com.myfitnesslog.support.JpaPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence for {@link WorkoutSession}: manual (null routine) sessions, enum
 * storage by name, and preservation of client-supplied domain timestamps
 * (startedAt/endedAt).
 */
@JpaPostgresTest
class WorkoutSessionPersistenceTest {

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private DefaultUserProvider defaultUserProvider;

    @Autowired
    private TestEntityManager entityManager;

    private WorkoutSession manualSession() {
        WorkoutSession session = new WorkoutSession();
        session.setId(UUID.randomUUID());
        session.setUser(defaultUserProvider.getReference());
        session.setRoutine(null);
        session.setStartedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return session;
    }

    @Test
    void persistsManualSessionWithDefaultStatus() {
        WorkoutSession saved = sessionRepository.saveAndFlush(manualSession());
        entityManager.clear();

        WorkoutSession found = sessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getRoutine()).isNull();
        assertThat(found.getStatus()).isEqualTo(WorkoutStatus.IN_PROGRESS);
        assertThat(found.getUser().getId()).isEqualTo(DefaultUserProvider.DEFAULT_USER_ID);
    }

    @Test
    void storesStatusEnumAsStringInColumn() {
        WorkoutSession session = manualSession();
        session.setStatus(WorkoutStatus.COMPLETED);
        WorkoutSession saved = sessionRepository.saveAndFlush(session);
        entityManager.flush();

        Object raw = entityManager.getEntityManager()
                .createNativeQuery("SELECT \"status\" FROM \"WorkoutSession\" WHERE \"id\" = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();
        assertThat(raw).isEqualTo("COMPLETED");
    }

    @Test
    void preservesClientSuppliedDomainTimestamps() {
        Instant startedAt = Instant.parse("2026-07-20T09:00:00Z");
        Instant endedAt = Instant.parse("2026-07-20T10:15:00Z");
        WorkoutSession session = manualSession();
        session.setStatus(WorkoutStatus.COMPLETED);
        session.setStartedAt(startedAt);
        session.setEndedAt(endedAt);

        WorkoutSession saved = sessionRepository.saveAndFlush(session);
        entityManager.clear();

        WorkoutSession found = sessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStartedAt()).isEqualTo(startedAt);
        assertThat(found.getEndedAt()).isEqualTo(endedAt);
    }
}
