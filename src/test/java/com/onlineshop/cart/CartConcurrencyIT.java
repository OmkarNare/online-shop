package com.onlineshop.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlineshop.inventory.InsufficientStockException;
import com.onlineshop.support.IntegrationTestBase;

/**
 * The guarantees that matter most under load, checked with real parallel
 * transactions against PostgreSQL.
 */
class CartConcurrencyIT extends IntegrationTestBase {

    @Autowired CartService cartService;

    @Test
    void highDemandItemIsNeverOversold() throws Exception {
        long console = createProduct("CONSOLE", true, 5);

        List<Outcome> outcomes = runInParallel(40, i -> () -> cartService.addItem("user-" + i, console, 1));

        assertThat(outcomes).filteredOn(o -> o == Outcome.OK).hasSize(5);
        assertThat(outcomes).filteredOn(o -> o == Outcome.SOLD_OUT).hasSize(35);
        assertThat(reserved(console)).isEqualTo(5);
        assertThat(cartItemRows()).isEqualTo(5);
    }

    @Test
    void parallelRequestsOfOneUserAreSerialisedWithoutLostUpdates() throws Exception {
        long console = createProduct("CONSOLE", true, 100);

        // 12 parallel "+1" requests for a brand-new cart (also races cart creation).
        // The per-line limit is 10, so exactly 10 must succeed.
        List<Outcome> outcomes = runInParallel(12, i -> () -> cartService.addItem("alice", console, 1));

        assertThat(outcomes).filteredOn(o -> o == Outcome.OK).hasSize(10);
        assertThat(outcomes).filteredOn(o -> o == Outcome.LIMIT).hasSize(2);
        assertThat(cartService.getCart("alice").items()).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualTo(10));
        assertThat(reserved(console)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart", Integer.class)).isEqualTo(1);
    }

    enum Outcome { OK, SOLD_OUT, LIMIT }

    interface TaskFactory {
        Callable<Object> task(int index);
    }

    private static List<Outcome> runInParallel(int count, TaskFactory factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Object> task = factory.task(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return Outcome.OK;
                    } catch (InsufficientStockException e) {
                        return Outcome.SOLD_OUT;
                    } catch (QuantityLimitExceededException e) {
                        return Outcome.LIMIT;
                    }
                }));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));   // any other exception fails the test
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
