package com.onlineshop.catalog;

import java.math.BigDecimal;
import java.time.Instant;

// Stock levels are not included on purpose: dashboards are cached and stock changes constantly.
public record ProductResponse(
        long id,
        String sku,
        String name,
        String category,
        BigDecimal price,
        String currency,
        boolean highDemand,
        Instant createdAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getCurrency(),
                product.isHighDemand(),
                product.getCreatedAt());
    }
}
