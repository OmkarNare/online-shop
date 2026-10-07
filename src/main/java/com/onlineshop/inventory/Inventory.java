package com.onlineshop.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Only used to create stock rows. Stock is changed through the conditional
 * updates in {@link InventoryRepository}, never by loading and saving the entity.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    protected Inventory() {
    }

    public Inventory(long productId, int onHand) {
        this.productId = productId;
        this.onHand = onHand;
        this.reserved = 0;
    }

    public Long getProductId() {
        return productId;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }
}
