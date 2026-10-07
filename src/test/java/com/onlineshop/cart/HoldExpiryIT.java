package com.onlineshop.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlineshop.inventory.InsufficientStockException;
import com.onlineshop.support.IntegrationTestBase;

class HoldExpiryIT extends IntegrationTestBase {

    @Autowired CartService cartService;
    @Autowired CartExpiryService expiryService;

    @Test
    void expiredHoldIsRemovedFromTheCartAndStockGoesBackToOtherCustomers() {
        long console = createProduct("CONSOLE", true, 1);
        cartService.addItem("alice", console, 1);
        assertThatThrownBy(() -> cartService.addItem("bob", console, 1))
                .isInstanceOf(InsufficientStockException.class);

        clock.advance(Duration.ofMinutes(15));

        // Hidden from Alice immediately, even before the sweeper runs
        assertThat(cartService.getCart("alice").items()).isEmpty();

        assertThat(expiryService.releaseNextBatch()).isEqualTo(1);
        assertThat(reserved(console)).isZero();
        assertThat(cartItemRows()).isZero();

        // Bob can now buy it
        assertThat(cartService.addItem("bob", console, 1).items()).hasSize(1);
        assertThat(reserved(console)).isEqualTo(1);
    }

    @Test
    void holdIsKeptUntilTheDeadline() {
        long console = createProduct("CONSOLE", true, 1);
        cartService.addItem("alice", console, 1);

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));

        assertThat(expiryService.releaseNextBatch()).isZero();
        assertThat(reserved(console)).isEqualTo(1);
        assertThat(cartService.getCart("alice").items()).singleElement()
                .satisfies(line -> assertThat(line.secondsRemaining()).isEqualTo(1L));
    }

    @Test
    void sweeperLeavesRegularItemsAlone() {
        long console = createProduct("CONSOLE", true, 1);
        long mug = createProduct("MUG", false, 10);
        cartService.addItem("alice", console, 1);
        cartService.addItem("alice", mug, 2);

        clock.advance(Duration.ofHours(1));
        expiryService.releaseNextBatch();

        assertThat(cartService.getCart("alice").items())
                .extracting(CartItemResponse::productId).containsExactly(mug);
    }

    @Test
    void userCanReAddAnItemWhoseHoldExpiredBeforeTheSweeperRan() {
        long console = createProduct("CONSOLE", true, 1);
        cartService.addItem("alice", console, 1);
        clock.advance(Duration.ofMinutes(16));

        // Same product, same cart: the expired row must be deleted before the new one is
        // inserted (unique cart_id+product_id), and release+reserve net out to no stock change.
        CartResponse view = cartService.addItem("alice", console, 1);

        assertThat(view.items()).singleElement()
                .satisfies(line -> assertThat(line.reservedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15))));
        assertThat(reserved(console)).isEqualTo(1);
        assertThat(cartItemRows()).isEqualTo(1);
    }
}
