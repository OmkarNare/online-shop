package com.onlineshop.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.onlineshop.support.IntegrationTestBase;

/** End-to-end over HTTP against real PostgreSQL + Redis. */
class CartApiIT extends IntegrationTestBase {

    @Test
    void highDemandItemLifecycleKeepsInventoryConsistent() throws Exception {
        long console = createProduct("CONSOLE", true, 5);

        add("alice", console, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].reservedUntil").value("2026-10-01T09:15:00Z"))
                .andExpect(jsonPath("$.items[0].secondsRemaining").value(900));
        assertThat(reserved(console)).isEqualTo(2);

        mvc.perform(patch("/api/v1/cart/items/{id}", console)
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));
        assertThat(reserved(console)).isEqualTo(3);

        mvc.perform(delete("/api/v1/cart/items/{id}", console).header("X-User-Id", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(reserved(console)).isZero();
        assertThat(cartItemRows()).isZero();
    }

    @Test
    void regularItemHasNoHoldAndReservesNothing() throws Exception {
        long mug = createProduct("MUG", false, 100);

        add("alice", mug, 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].reservedUntil").doesNotExist())
                .andExpect(jsonPath("$.totalQuantity").value(4))
                .andExpect(jsonPath("$.total").value(40.00));
        assertThat(reserved(mug)).isZero();

        mvc.perform(get("/api/v1/cart").header("X-User-Id", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(mug));
    }

    @Test
    void requestingMoreThanTheRemainingStockIsRejected() throws Exception {
        long console = createProduct("CONSOLE", true, 3);
        add("alice", console, 2).andExpect(status().isCreated());

        add("bob", console, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(reserved(console)).isEqualTo(2);   // bob's failed attempt left nothing behind
        mvc.perform(get("/api/v1/cart").header("X-User-Id", "bob"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void unknownProductIsRejected() throws Exception {
        add("alice", 999, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void cartsAreIsolatedPerUser() throws Exception {
        long mug = createProduct("MUG", false, 100);
        add("alice", mug, 1).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/cart").header("X-User-Id", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private ResultActions add(String user, long productId, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/cart/items")
                .header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + "}"));
    }
}
