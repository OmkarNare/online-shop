package com.onlineshop.cart;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlineshop.web.ApiHeaders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(
            @RequestHeader(ApiHeaders.USER_ID) @NotBlank @Size(max = 64) String userId) {
        return cartService.getCart(userId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(
            @RequestHeader(ApiHeaders.USER_ID) @NotBlank @Size(max = 64) String userId,
            @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(userId, request.productId(), request.quantity());
    }

    @PatchMapping("/items/{productId}")
    public CartResponse updateItemQuantity(
            @RequestHeader(ApiHeaders.USER_ID) @NotBlank @Size(max = 64) String userId,
            @PathVariable @Positive long productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItemQuantity(userId, productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(
            @RequestHeader(ApiHeaders.USER_ID) @NotBlank @Size(max = 64) String userId,
            @PathVariable @Positive long productId) {
        return cartService.removeItem(userId, productId);
    }
}
