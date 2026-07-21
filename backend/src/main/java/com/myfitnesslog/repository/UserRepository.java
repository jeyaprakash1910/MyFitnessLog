package com.myfitnesslog.repository;

import com.myfitnesslog.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data repository for {@link User}. */
public interface UserRepository extends JpaRepository<User, UUID> {
}
