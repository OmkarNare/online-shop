package com.onlineshop.catalog;

public class ProductNotFoundException extends RuntimeException {

    private final long productId;

    public ProductNotFoundException(long productId) {
        super("Product " + productId + " does not exist or is not available");
        this.productId = productId;
    }

    public long getProductId() {
        return productId;
    }
}
