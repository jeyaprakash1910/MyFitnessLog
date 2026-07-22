package com.myfitnesslog.repository;

import com.myfitnesslog.entity.WorkoutSession;
import com.myfitnesslog.entity.WorkoutStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link WorkoutSession}. */
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {

    /**
     * Sessions in a given status, newest first — the workout history query.
     *
     * <p>Filtering and ordering happen in SQL so the indexes on {@code status} and
     * {@code startedAt} are used instead of loading every row and sorting in Java.
     * The {@link Pageable} parameter is what makes pagination a caller-side change
     * rather than a rewrite; {@code Pageable.unpaged()} returns everything.
     */
    List<WorkoutSession> findByStatusOrderByStartedAtDesc(WorkoutStatus status, Pageable pageable);
}
