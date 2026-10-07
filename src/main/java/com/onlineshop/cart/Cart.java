package com.onlineshop.cart;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.onlineshop.catalog.Product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt ASC, id ASC")
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {
    }

    public Cart(String userId, Instant createdAt) {
        this.userId = userId;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Optional<CartItem> findItem(long productId) {
        return items.stream()
                .filter(item -> item.getProductId() == productId)
                .findFirst();
    }

    CartItem addItem(Product product, int quantity, Instant addedAt, Instant reservedUntil) {
        CartItem item = new CartItem(this, product, quantity, addedAt, reservedUntil);
        items.add(item);
        return item;
    }

    void removeItem(CartItem item) {
        items.remove(item);
    }

    /**
     * Removes items whose hold has expired and returns them, so the caller can
     * release their stock.
     */
    List<CartItem> removeExpiredItems(Instant now) {
        List<CartItem> expiredItems = items.stream()
                .filter(item -> item.isExpired(now))
                .toList();
        items.removeAll(expiredItems);
        return expiredItems;
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }
}
