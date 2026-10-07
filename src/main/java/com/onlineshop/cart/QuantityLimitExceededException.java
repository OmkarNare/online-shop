package com.onlineshop.cart;

public class QuantityLimitExceededException extends RuntimeException {

    public QuantityLimitExceededException(long productId, int requestedQuantity, int maxQuantity) {
        super("Quantity " + requestedQuantity + " for product " + productId
                + " exceeds the limit of " + maxQuantity);
    }
}
