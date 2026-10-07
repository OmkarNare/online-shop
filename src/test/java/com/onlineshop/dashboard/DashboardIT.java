package com.onlineshop.dashboard;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlineshop.cart.CartService;
import com.onlineshop.support.IntegrationTestBase;

class DashboardIT extends IntegrationTestBase {

    @Autowired CartService cartService;
    @Autowired PopularityRankingJob rankingJob;

    @Test
    void newItemsAreNewestFirst() throws Exception {
        createProduct("OLDEST", false, 1);
        clock.advance(Duration.ofMinutes(1));
        createProduct("MIDDLE", false, 1);
        clock.advance(Duration.ofMinutes(1));
        createProduct("NEWEST", false, 1);

        mvc.perform(get("/api/v1/dashboards/new"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=10, public"))
                .andExpect(jsonPath("$.items[*].sku", contains("NEWEST", "MIDDLE", "OLDEST")));
    }

    @Test
    void popularItemsAreRankedByUnitsAddedToCarts() throws Exception {
        long kettle = createProduct("KETTLE", false, 100);
        long mug = createProduct("MUG", false, 100);
        long book = createProduct("BOOK", false, 100);

        cartService.addItem("alice", mug, 3);
        cartService.addItem("bob", mug, 2);       // mug: 5
        cartService.addItem("carol", book, 3);    // book: 3
        cartService.addItem("dave", kettle, 1);   // kettle: 1

        rankingJob.refreshRanking();

        mvc.perform(get("/api/v1/dashboards/popular").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("POPULAR"))
                .andExpect(jsonPath("$.items[*].sku", contains("MUG", "BOOK", "KETTLE")));
    }

    @Test
    void popularityOutsideTheSevenDayWindowNoLongerCounts() throws Exception {
        long oldHit = createProduct("OLD-HIT", false, 100);
        long recent = createProduct("RECENT", false, 100);

        cartService.addItem("alice", oldHit, 9);
        cartService.removeItem("alice", oldHit);   // keep the DB fallback out of the picture
        clock.advance(Duration.ofDays(8));
        cartService.addItem("bob", recent, 1);

        rankingJob.refreshRanking();

        mvc.perform(get("/api/v1/dashboards/popular"))
                .andExpect(jsonPath("$.items[*].sku", contains("RECENT")));
    }

    @Test
    void popularFallsBackToCartContentsWhenNoRankingExistsYet() throws Exception {
        long kettle = createProduct("KETTLE", false, 100);
        long mug = createProduct("MUG", false, 100);
        cartService.addItem("alice", kettle, 1);
        cartService.addItem("bob", mug, 4);
        // ranking job has not run: snapshot key does not exist

        mvc.perform(get("/api/v1/dashboards/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sku", contains("MUG", "KETTLE")));
    }
}
