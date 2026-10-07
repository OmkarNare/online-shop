package com.onlineshop.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class AppConfig {

    /** The shop operates only in London, so "today" and business time are London time. */
    public static final ZoneId SHOP_ZONE = ZoneId.of("Europe/London");

    /** Injected everywhere instead of calling Instant.now(), so hold expiry is testable. */
    @Bean
    public Clock clock() {
        return Clock.system(SHOP_ZONE);
    }
}
