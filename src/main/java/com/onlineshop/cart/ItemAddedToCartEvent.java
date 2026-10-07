package com.onlineshop.cart;

public record ItemAddedToCartEvent(long productId, int quantity) {
}
