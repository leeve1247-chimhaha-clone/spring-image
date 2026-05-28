package com.multirkh.chimhahaimage.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class ZonedDateTimeConfig {
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> java.util.Optional.of(java.time.ZonedDateTime.now());
    }
}
