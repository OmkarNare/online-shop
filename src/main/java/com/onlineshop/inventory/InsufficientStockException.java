package com.onlineshop.inventory;

public class InsufficientStockException extends RuntimeException {

    private final long productId;

    public InsufficientStockException(long productId, int requestedQuantity) {
        super("Not enough stock for product " + productId + " (requested " + requestedQuantity + ")");
        this.productId = productId;
    }

    public long getProductId() {
        return productId;
    }
}
