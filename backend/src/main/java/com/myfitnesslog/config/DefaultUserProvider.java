package com.myfitnesslog.config;

import com.myfitnesslog.entity.User;
import com.myfitnesslog.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Supplies the single Version 1 application user. Authentication is out of scope
 * for V1 and the Android client does not send a userId, so the backend attaches
 * this seeded default user (V4__Seed_default_user.sql) to every routine and
 * workout session it persists.
 *
 * {@link #getReference()} returns a lazy JPA reference (no SELECT is issued);
 * setting it as an association only writes the FK, which is all persistence needs.
 */
@Component
public class DefaultUserProvider {

    /** Fixed identifier of the single V1 user; must match V4__Seed_default_user.sql. */
    public static final UUID DEFAULT_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository userRepository;

    public DefaultUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID getDefaultUserId() {
        return DEFAULT_USER_ID;
    }

    /** A lazy reference to the default user, suitable for setting as an FK association. */
    public User getReference() {
        return userRepository.getReferenceById(DEFAULT_USER_ID);
    }
}
