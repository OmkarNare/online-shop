package com.onlineshop.dashboard;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop.dashboard")
public record DashboardProperties(
        int maxItems,
        int popularityWindowDays,
        Duration popularRefreshInterval) {
}
