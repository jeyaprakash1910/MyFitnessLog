package com.myfitnesslog.support;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.config.JpaAuditingConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed annotation for JPA slice tests that run against the real PostgreSQL
 * test database (see src/test/resources/application.yml).
 *
 * {@code replace = NONE} keeps the configured PostgreSQL datasource instead of
 * substituting an embedded H2, so Flyway migrations and Postgres-specific schema
 * behaviour (quoted identifiers, CHECK constraints, enum/NUMERIC storage) are
 * exercised. {@code @DataJpaTest} is transactional and rolls back per test, so
 * tests stay isolated.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, DefaultUserProvider.class})
public @interface JpaPostgresTest {
}
