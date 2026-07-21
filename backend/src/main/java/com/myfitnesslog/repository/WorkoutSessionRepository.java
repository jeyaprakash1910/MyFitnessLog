package com.myfitnesslog.repository;

import com.myfitnesslog.entity.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data repository for {@link WorkoutSession}. */
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {
}
