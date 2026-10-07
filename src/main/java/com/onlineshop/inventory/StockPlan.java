package com.onlineshop.inventory;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the stock changes of one transaction and applies them together.
 * <p>
 * Changes are netted per product and applied in ascending product id order.
 * Because every transaction locks inventory rows in the same order, they
 * cannot deadlock each other.
 */
public final class StockPlan {

    private static final Logger log = LoggerFactory.getLogger(StockPlan.class);

    private final SortedMap<Long, Integer> changesByProductId = new TreeMap<>();

    public StockPlan reserve(long productId, int quantity) {
        return addChange(productId, quantity);
    }

    public StockPlan release(long productId, int quantity) {
        return addChange(productId, -quantity);
    }

    private StockPlan addChange(long productId, int change) {
        if (change != 0) {
            changesByProductId.merge(productId, change, Integer::sum);
        }
        return this;
    }

    public Map<Long, Integer> getNetChanges() {
        SortedMap<Long, Integer> netChanges = new TreeMap<>(changesByProductId);
        netChanges.values().removeIf(change -> change == 0);
        return Collections.unmodifiableMap(netChanges);
    }

    /**
     * Must be called inside the caller's transaction, so a failed reservation
     * rolls back everything else done in it.
     *
     * @throws InsufficientStockException if a reservation cannot be made
     */
    public void applyTo(InventoryRepository inventoryRepository) {
        getNetChanges().forEach((productId, change) -> {
            if (change > 0) {
                if (inventoryRepository.tryReserve(productId, change) == 0) {
                    throw new InsufficientStockException(productId, change);
                }
            } else if (inventoryRepository.release(productId, -change) == 0) {
                log.warn("Could not release {} units of product {}", -change, productId);
            }
        });
    }
}
