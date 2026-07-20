package com.myfitnesslog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA Auditing so that @CreatedDate and @LastModifiedDate
 * fields (see AbstractAuditableEntity / ADR-0006) are populated automatically.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
