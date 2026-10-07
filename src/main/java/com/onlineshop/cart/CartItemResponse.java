package com.onlineshop.cart;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlineshop.catalog.Product;

/**
 * {@code reservedUntil} and {@code secondsRemaining} are only present for
 * high-demand items that are held in the cart.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartItemResponse(
        long productId,
        String sku,
        String name,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        Instant reservedUntil,
        Long secondsRemaining) {

    static CartItemResponse from(CartItem item, Instant now) {
        Product product = item.getProduct();
        BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        Long secondsRemaining = null;
        if (item.isHeld()) {
            secondsRemaining = Math.max(0, Duration.between(now, item.getReservedUntil()).toSeconds());
        }

        return new CartItemResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getPrice(),
                item.getQuantity(),
                lineTotal,
                item.getReservedUntil(),
                secondsRemaining);
    }
}
