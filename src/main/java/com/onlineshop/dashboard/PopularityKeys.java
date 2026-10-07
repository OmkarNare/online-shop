package com.onlineshop.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Redis keys used for popularity:
 * <ul>
 *   <li>{@code shop:popularity:day:<date>} - units added to carts on that London day</li>
 *   <li>{@code shop:popularity:ranking} - merged ranking for the last N days</li>
 * </ul>
 */
final class PopularityKeys {

    static final String RANKING = "shop:popularity:ranking";

    private static final String DAY_KEY_PREFIX = "shop:popularity:day:";

    private PopularityKeys() {
    }

    static String forDay(LocalDate date) {
        return DAY_KEY_PREFIX + date;
    }

    // Newest day first.
    static List<String> forLastDays(LocalDate today, int days) {
        return IntStream.range(0, days)
                .mapToObj(daysAgo -> forDay(today.minusDays(daysAgo)))
                .toList();
    }
}
