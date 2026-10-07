package com.onlineshop.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.onlineshop.config.AppConfig;

/**
 * Merges the daily popularity counts of the last N days into one ranking, so
 * reading the dashboard is a single Redis call. Safe to run on every instance
 * because ZUNIONSTORE replaces the result atomically.
 */
@Component
public class PopularityRankingJob {

    private static final Logger log = LoggerFactory.getLogger(PopularityRankingJob.class);

    static final int MAX_RANKING_SIZE = 500;

    private final StringRedisTemplate redisTemplate;
    private final DashboardProperties dashboardProperties;
    private final Clock clock;

    public PopularityRankingJob(StringRedisTemplate redisTemplate,
                                DashboardProperties dashboardProperties,
                                Clock clock) {
        this.redisTemplate = redisTemplate;
        this.dashboardProperties = dashboardProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${shop.dashboard.popular-refresh-interval}")
    public void refreshRanking() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), AppConfig.SHOP_ZONE);
        List<String> dayKeys = PopularityKeys.forLastDays(today, dashboardProperties.popularityWindowDays());
        try {
            redisTemplate.opsForZSet().unionAndStore(dayKeys.get(0), dayKeys.subList(1, dayKeys.size()),
                    PopularityKeys.RANKING);
            // Keep only the top entries; rank 0 is the lowest score.
            redisTemplate.opsForZSet().removeRange(PopularityKeys.RANKING, 0, -(MAX_RANKING_SIZE + 1L));
        } catch (DataAccessException e) {
            log.warn("Could not refresh popularity ranking: {}", e.getMessage());
        }
    }
}
