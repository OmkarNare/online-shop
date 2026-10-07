package com.onlineshop.cart;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlineshop.catalog.Product;
import com.onlineshop.catalog.ProductNotFoundException;
import com.onlineshop.catalog.ProductRepository;
import com.onlineshop.inventory.InsufficientStockException;
import com.onlineshop.inventory.InventoryRepository;
import com.onlineshop.inventory.StockPlan;

/**
 * Every write locks the user's cart row first and then applies stock changes
 * in product id order. The expiry job locks in the same order, so the two
 * cannot deadlock.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CartProperties cartProperties;
    private final Clock clock;

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       InventoryRepository inventoryRepository,
                       ApplicationEventPublisher eventPublisher,
                       CartProperties cartProperties,
                       Clock clock) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.cartProperties = cartProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(String userId) {
        Instant now = clock.instant();
        return cartRepository.findByUserId(userId)
                .map(cart -> CartResponse.from(cart, now))
                .orElseGet(() -> CartResponse.empty(userId));
    }

    @Transactional
    public CartResponse addItem(String userId, long productId, int quantity) {
        validateQuantity(quantity);
        Instant now = clock.instant();

        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        Cart cart = lockOrCreateCart(userId, now);
        StockPlan stockPlan = new StockPlan();
        removeExpiredItems(cart, now, stockPlan);

        CartItem existingItem = cart.findItem(productId).orElse(null);
        int currentQuantity = existingItem == null ? 0 : existingItem.getQuantity();
        setItemQuantity(cart, product, existingItem, currentQuantity + quantity, now, stockPlan);

        stockPlan.applyTo(inventoryRepository);
        cart.touch(now);
        eventPublisher.publishEvent(new ItemAddedToCartEvent(productId, quantity));
        return CartResponse.from(cart, now);
    }

    @Transactional
    public CartResponse updateItemQuantity(String userId, long productId, int quantity) {
        validateQuantity(quantity);
        Instant now = clock.instant();

        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
        StockPlan stockPlan = new StockPlan();
        removeExpiredItems(cart, now, stockPlan);

        CartItem item = cart.findItem(productId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
        int addedQuantity = quantity - item.getQuantity();
        setItemQuantity(cart, item.getProduct(), item, quantity, now, stockPlan);

        stockPlan.applyTo(inventoryRepository);
        cart.touch(now);
        if (addedQuantity > 0) {
            eventPublisher.publishEvent(new ItemAddedToCartEvent(productId, addedQuantity));
        }
        return CartResponse.from(cart, now);
    }

    /**
     * Removing a product that is not in the cart is not treated as an error.
     */
    @Transactional
    public CartResponse removeItem(String userId, long productId) {
        Instant now = clock.instant();

        Optional<Cart> existingCart = cartRepository.findByUserIdForUpdate(userId);
        if (existingCart.isEmpty()) {
            return CartResponse.empty(userId);
        }

        Cart cart = existingCart.get();
        StockPlan stockPlan = new StockPlan();
        removeExpiredItems(cart, now, stockPlan);

        cart.findItem(productId).ifPresent(item -> {
            if (item.isHeld()) {
                stockPlan.release(productId, item.getQuantity());
            }
            cart.removeItem(item);
        });

        stockPlan.applyTo(inventoryRepository);
        cart.touch(now);
        return CartResponse.from(cart, now);
    }

    private void setItemQuantity(Cart cart, Product product, CartItem item, int newQuantity,
                                 Instant now, StockPlan stockPlan) {
        long productId = product.getId();
        int maxQuantity = cartProperties.maxQuantityPerItem();
        if (newQuantity > maxQuantity) {
            throw new QuantityLimitExceededException(productId, newQuantity, maxQuantity);
        }

        boolean itemIsHeld = item != null && item.isHeld();
        if (product.isHighDemand() || itemIsHeld) {
            updateHeldItem(cart, product, item, newQuantity, now, stockPlan);
        } else {
            updateRegularItem(cart, product, item, newQuantity, now);
        }
    }

    // High-demand item: stock is reserved now and the item is removed when the hold expires.
    private void updateHeldItem(Cart cart, Product product, CartItem item, int newQuantity,
                                Instant now, StockPlan stockPlan) {
        int heldQuantity = (item != null && item.isHeld()) ? item.getQuantity() : 0;
        int difference = newQuantity - heldQuantity;
        if (difference > 0) {
            stockPlan.reserve(product.getId(), difference);
        } else if (difference < 0) {
            stockPlan.release(product.getId(), -difference);
        }

        Instant holdUntil = now.plus(cartProperties.holdDuration());
        if (item == null) {
            cart.addItem(product, newQuantity, now, holdUntil);
        } else {
            item.setQuantity(newQuantity);
            // Adding more of the same item must not extend the original deadline.
            item.startHoldIfNotHeld(holdUntil);
        }
    }

    // Regular item: nothing is reserved, availability is checked again at checkout.
    private void updateRegularItem(Cart cart, Product product, CartItem item, int newQuantity, Instant now) {
        int available = inventoryRepository.findAvailableQuantity(product.getId()).orElse(0);
        if (available < newQuantity) {
            throw new InsufficientStockException(product.getId(), newQuantity);
        }

        if (item == null) {
            cart.addItem(product, newQuantity, now, null);
        } else {
            item.setQuantity(newQuantity);
        }
    }

    private void removeExpiredItems(Cart cart, Instant now, StockPlan stockPlan) {
        List<CartItem> expiredItems = cart.removeExpiredItems(now);
        for (CartItem expiredItem : expiredItems) {
            stockPlan.release(expiredItem.getProductId(), expiredItem.getQuantity());
        }

        if (!expiredItems.isEmpty()) {
            // Hibernate runs inserts before deletes on flush. Flush now so that re-adding
            // the same product does not violate the (cart_id, product_id) unique key.
            cartRepository.flush();
        }
    }

    private Cart lockOrCreateCart(String userId, Instant now) {
        Optional<Cart> cart = cartRepository.findByUserIdForUpdate(userId);
        if (cart.isPresent()) {
            return cart.get();
        }

        cartRepository.insertIfAbsent(userId, now);
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Could not create cart for user " + userId));
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
    }
}
