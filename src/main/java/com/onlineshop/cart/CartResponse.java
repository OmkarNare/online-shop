package com.onlineshop.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
        String userId,
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal total,
        String currency) {

    public static final String CURRENCY = "GBP";

    public static CartResponse empty(String userId) {
        return new CartResponse(userId, List.of(), 0, BigDecimal.ZERO.setScale(2), CURRENCY);
    }

    /**
     * Lines whose hold has already expired are left out, even if the sweeper
     * has not removed them from the database yet.
     */
    public static CartResponse from(Cart cart, Instant now) {
        List<CartItemResponse> items = cart.getItems().stream()
                .filter(item -> !item.isExpired(now))
                .map(item -> CartItemResponse.from(item, now))
                .toList();

        int totalQuantity = items.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();
        BigDecimal total = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        return new CartResponse(cart.getUserId(), items, totalQuantity, total, CURRENCY);
    }
}
