package com.myfitnesslog.repository;

import com.myfitnesslog.entity.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data repository for {@link Routine}. */
public interface RoutineRepository extends JpaRepository<Routine, UUID> {
}
