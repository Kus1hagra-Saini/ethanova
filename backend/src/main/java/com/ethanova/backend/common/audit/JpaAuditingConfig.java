package com.ethanova.backend.common.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Enables Spring Data JPA auditing for {@code @CreatedDate} /
 * {@code @LastModifiedDate} fields on {@code BaseAuditableEntity}.
 *
 * <p>Provides a custom {@link DateTimeProvider} that returns
 * {@link OffsetDateTime} in UTC. The default provider returns
 * {@code LocalDateTime}, which Spring Data cannot implicitly convert to
 * {@code OffsetDateTime} — the entity's audit field type — because there
 * is no safe assumed offset. Supplying the provider explicitly resolves
 * the type at the source.
 *
 * <p>UTC is used deliberately: all timestamps are stored zone-anchored in
 * PostgreSQL {@code TIMESTAMPTZ} columns; clients (Power BI, dashboards)
 * convert to local time for display. Storing in UTC keeps the model
 * unambiguous regardless of JVM or DB server timezone.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}