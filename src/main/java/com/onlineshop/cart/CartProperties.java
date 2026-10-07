package com.onlineshop.cart;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop.cart")
public record CartProperties(
        Duration holdDuration,
        int maxQuantityPerItem,
        Duration expirySweepInterval,
        int expirySweepBatchSize) {
}
