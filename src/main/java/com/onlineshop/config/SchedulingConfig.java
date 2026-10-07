package com.onlineshop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background jobs (hold-expiry sweeper, popularity ranking refresh).
 * Disabled in tests, which trigger the jobs explicitly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "shop.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
