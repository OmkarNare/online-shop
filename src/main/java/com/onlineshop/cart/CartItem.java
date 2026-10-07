package com.onlineshop.cart;

import java.time.Instant;

import com.onlineshop.catalog.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    // Null for regular items. For held items the quantity is counted in inventory.reserved until this time.
    @Column(name = "reserved_until")
    private Instant reservedUntil;

    protected CartItem() {
    }

    CartItem(Cart cart, Product product, int quantity, Instant addedAt, Instant reservedUntil) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
        this.addedAt = addedAt;
        this.reservedUntil = reservedUntil;
    }

    public long getProductId() {
        return product.getId();
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }

    public boolean isHeld() {
        return reservedUntil != null;
    }

    public boolean isExpired(Instant now) {
        return reservedUntil != null && !reservedUntil.isAfter(now);
    }

    void startHoldIfNotHeld(Instant holdUntil) {
        if (reservedUntil == null) {
            reservedUntil = holdUntil;
        }
    }
}
