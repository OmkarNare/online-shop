package com.onlineshop.dashboard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.onlineshop.cart.ItemAddedToCartEvent;
import com.onlineshop.support.MutableClock;

@ExtendWith(MockitoExtension.class)
class PopularityJobsTest {

    /** 23:30 UTC on 1 Oct is already 00:30 on 2 Oct in London (BST). */
    static final Instant LATE_EVENING_UTC = Instant.parse("2026-10-01T23:30:00Z");

    @Mock StringRedisTemplate redisTemplate;
    @Mock ZSetOperations<String, String> zset;

    MutableClock clock = new MutableClock(LATE_EVENING_UTC);
    DashboardProperties properties = new DashboardProperties(50, 7, Duration.ofMinutes(1));

    @Test
    void recorderCountsUnitsInTheLondonDayBucket() {
        when(redisTemplate.opsForZSet()).thenReturn(zset);

        new PopularityRecorder(redisTemplate, properties, clock).onItemAddedToCart(new ItemAddedToCartEvent(42L, 3));

        verify(zset).incrementScore("shop:popularity:day:2026-10-02", "42", 3.0);
        verify(redisTemplate).expire("shop:popularity:day:2026-10-02", Duration.ofDays(8));
    }

    @Test
    void recorderNeverFailsTheCallerWhenRedisIsDown() {
        when(redisTemplate.opsForZSet()).thenReturn(zset);
        when(zset.incrementScore(anyString(), anyString(), anyDouble()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> new PopularityRecorder(redisTemplate, properties, clock).onItemAddedToCart(new ItemAddedToCartEvent(1L, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void rankingJobUnionsTheLastSevenLondonDaysAndTrimsTheSnapshot() {
        when(redisTemplate.opsForZSet()).thenReturn(zset);

        new PopularityRankingJob(redisTemplate, properties, clock).refreshRanking();

        verify(zset).unionAndStore(
                "shop:popularity:day:2026-10-02",
                List.of("shop:popularity:day:2026-10-01",
                        "shop:popularity:day:2026-09-30",
                        "shop:popularity:day:2026-09-29",
                        "shop:popularity:day:2026-09-28",
                        "shop:popularity:day:2026-09-27",
                        "shop:popularity:day:2026-09-26"),
                PopularityKeys.RANKING);
        verify(zset).removeRange(PopularityKeys.RANKING, 0, -(PopularityRankingJob.MAX_RANKING_SIZE + 1L));
    }
}
