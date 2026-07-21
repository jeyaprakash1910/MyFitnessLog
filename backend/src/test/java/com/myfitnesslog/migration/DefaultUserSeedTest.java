package com.myfitnesslog.migration;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.entity.User;
import com.myfitnesslog.repository.UserRepository;
import com.myfitnesslog.support.JpaPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Flyway migrations (V1–V4) run and seed exactly one default
 * user whose id matches {@link DefaultUserProvider#DEFAULT_USER_ID}. Because
 * Flyway applies each versioned migration once and records it in
 * flyway_schema_history, re-running the suite never inserts a second user.
 */
@JpaPostgresTest
class DefaultUserSeedTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultUserProvider defaultUserProvider;

    @Test
    void seedsExactlyOneDefaultUser() {
        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getId()).isEqualTo(DefaultUserProvider.DEFAULT_USER_ID);
        assertThat(users.get(0).isDeleted()).isFalse();
    }

    @Test
    void defaultUserProviderResolvesTheSeededUser() {
        User reference = defaultUserProvider.getReference();
        assertThat(reference.getId()).isEqualTo(DefaultUserProvider.DEFAULT_USER_ID);
        assertThat(defaultUserProvider.getDefaultUserId())
                .isEqualTo(DefaultUserProvider.DEFAULT_USER_ID);
    }
}
