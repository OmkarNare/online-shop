package com.onlineshop.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlineshop.cart.ItemAddedToCartEvent;
import com.onlineshop.config.AppConfig;

/**
 * Counts units added to carts per London day. Runs after commit so rolled-back
 * requests are not counted. A Redis failure must not fail the cart request,
 * so it is only logged.
 */
@Component
public class PopularityRecorder {

    private static final Logger log = LoggerFactory.getLogger(PopularityRecorder.class);

    private final StringRedisTemplate redisTemplate;
    private final DashboardProperties dashboardProperties;
    private final Clock clock;

    public PopularityRecorder(StringRedisTemplate redisTemplate,
                              DashboardProperties dashboardProperties,
                              Clock clock) {
        this.redisTemplate = redisTemplate;
        this.dashboardProperties = dashboardProperties;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemAddedToCart(ItemAddedToCartEvent event) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), AppConfig.SHOP_ZONE);
        String key = PopularityKeys.forDay(today);
        try {
            redisTemplate.opsForZSet().incrementScore(key, String.valueOf(event.productId()), event.quantity());
            redisTemplate.expire(key, Duration.ofDays(dashboardProperties.popularityWindowDays() + 1L));
        } catch (DataAccessException e) {
            log.warn("Could not record popularity for product {}: {}", event.productId(), e.getMessage());
        }
    }
}
