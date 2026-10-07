package com.onlineshop.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Returns stock from expired holds to the pool. Runs on every instance;
 * SKIP LOCKED in the batch query keeps instances from processing the same cart.
 */
@Component
public class CartHoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(CartHoldExpiryJob.class);
    private static final int MAX_BATCHES_PER_RUN = 50;

    private final CartExpiryService cartExpiryService;

    public CartHoldExpiryJob(CartExpiryService cartExpiryService) {
        this.cartExpiryService = cartExpiryService;
    }

    @Scheduled(fixedDelayString = "${shop.cart.expiry-sweep-interval}")
    public void run() {
        int processedCarts = 0;
        try {
            for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
                int processed = cartExpiryService.releaseNextBatch();
                processedCarts += processed;
                if (processed < cartExpiryService.getBatchSize()) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.error("Hold expiry job failed after {} carts, will retry on next run", processedCarts, e);
            return;
        }

        if (processedCarts > 0) {
            log.info("Released expired holds in {} carts", processedCarts);
        }
    }
}
