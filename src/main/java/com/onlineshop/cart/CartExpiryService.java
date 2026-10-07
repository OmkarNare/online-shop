package com.onlineshop.cart;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlineshop.inventory.InventoryRepository;
import com.onlineshop.inventory.StockPlan;

@Service
public class CartExpiryService {

    private final CartRepository cartRepository;
    private final InventoryRepository inventoryRepository;
    private final CartProperties cartProperties;
    private final Clock clock;

    public CartExpiryService(CartRepository cartRepository,
                             InventoryRepository inventoryRepository,
                             CartProperties cartProperties,
                             Clock clock) {
        this.cartRepository = cartRepository;
        this.inventoryRepository = inventoryRepository;
        this.cartProperties = cartProperties;
        this.clock = clock;
    }

    /**
     * Removes expired items from one batch of carts and returns their stock.
     *
     * @return the number of carts processed
     */
    @Transactional
    public int releaseNextBatch() {
        Instant now = clock.instant();
        List<Long> cartIds = cartRepository.lockCartsWithExpiredHolds(now, getBatchSize());
        if (cartIds.isEmpty()) {
            return 0;
        }

        StockPlan stockPlan = new StockPlan();
        for (Cart cart : cartRepository.findByIdIn(cartIds)) {
            for (CartItem expiredItem : cart.removeExpiredItems(now)) {
                stockPlan.release(expiredItem.getProductId(), expiredItem.getQuantity());
            }
        }
        stockPlan.applyTo(inventoryRepository);
        return cartIds.size();
    }

    public int getBatchSize() {
        return cartProperties.expirySweepBatchSize();
    }
}
