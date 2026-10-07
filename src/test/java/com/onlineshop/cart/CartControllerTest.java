package com.onlineshop.cart;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.onlineshop.catalog.ProductNotFoundException;
import com.onlineshop.inventory.InsufficientStockException;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CartService cartService;

    @Test
    void addItemReturns201WithTheHoldDeadline() throws Exception {
        CartItemResponse line = new CartItemResponse(1L, "CONSOLE", "Console", new BigDecimal("499.99"), 1,
                new BigDecimal("499.99"), Instant.parse("2026-10-01T10:15:00Z"), 900L);
        when(cartService.addItem("alice", 1L, 1))
                .thenReturn(new CartResponse("alice", List.of(line), 1, new BigDecimal("499.99"), "GBP"));

        mvc.perform(post("/api/v1/cart/items")
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].reservedUntil").value("2026-10-01T10:15:00Z"))
                .andExpect(jsonPath("$.items[0].secondsRemaining").value(900))
                .andExpect(jsonPath("$.currency").value("GBP"));
    }

    @Test
    void normalItemsOmitHoldFields() throws Exception {
        CartItemResponse line = new CartItemResponse(2L, "MUG", "Mug", new BigDecimal("9.50"), 2,
                new BigDecimal("19.00"), null, null);
        when(cartService.getCart("alice"))
                .thenReturn(new CartResponse("alice", List.of(line), 2, new BigDecimal("19.00"), "GBP"));

        mvc.perform(get("/api/v1/cart").header("X-User-Id", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reservedUntil").doesNotExist())
                .andExpect(jsonPath("$.total").value(19.00));
    }

    @Test
    void missingUserHeaderIsRejected() throws Exception {
        mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cartService);
    }

    @Test
    void invalidQuantityIsRejected() throws Exception {
        mvc.perform(post("/api/v1/cart/items")
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cartService);
    }

    @Test
    void soldOutReturns409WithAStableErrorCode() throws Exception {
        when(cartService.addItem(anyString(), anyLong(), anyInt()))
                .thenThrow(new InsufficientStockException(1L, 1));

        mvc.perform(post("/api/v1/cart/items")
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        when(cartService.addItem("alice", 9L, 1)).thenThrow(new ProductNotFoundException(9L));

        mvc.perform(post("/api/v1/cart/items")
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":9,\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void updatingAnItemThatIsNotInTheCartReturns404() throws Exception {
        when(cartService.updateItemQuantity("alice", 1L, 2)).thenThrow(new CartItemNotFoundException(1L));

        mvc.perform(patch("/api/v1/cart/items/1")
                        .header("X-User-Id", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void removeItemReturnsTheUpdatedCart() throws Exception {
        when(cartService.removeItem("alice", 1L)).thenReturn(CartResponse.empty("alice"));

        mvc.perform(delete("/api/v1/cart/items/1").header("X-User-Id", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
