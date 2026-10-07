package com.onlineshop.cart;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(long productId) {
        super("Product " + productId + " is not in the cart");
    }
}
