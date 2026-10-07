package com.onlineshop.cart;

import static com.onlineshop.cart.CartServiceTest.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.onlineshop.catalog.Product;
import com.onlineshop.inventory.InventoryRepository;
import com.onlineshop.support.MutableClock;

@ExtendWith(MockitoExtension.class)
class CartExpiryServiceTest {

    static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    static final Duration HOLD = Duration.ofMinutes(15);

    @Mock CartRepository cartRepository;
    @Mock InventoryRepository inventoryRepository;

    MutableClock clock = new MutableClock(T0);
    CartExpiryService service;

    Product console = product(1L, "CONSOLE", true, "499.99");
    Product trainers = product(5L, "TRAINERS", true, "180.00");
    Product mug = product(2L, "MUG", false, "9.50");

    @BeforeEach
    void setUp() {
        service = new CartExpiryService(cartRepository, inventoryRepository, new CartProperties(HOLD, 10, Duration.ofSeconds(15), 100), clock);
    }

    @Test
    void releasesExpiredItemsAcrossCartsInOneNettedUpdatePerProduct() {
        Cart alice = new Cart("alice", T0);
        alice.addItem(console, 2, T0, T0.plus(HOLD));
        alice.addItem(mug, 4, T0, null);
        Cart bob = new Cart("bob", T0);
        bob.addItem(console, 1, T0, T0.plus(HOLD));
        bob.addItem(trainers, 1, T0.plus(Duration.ofMinutes(10)), T0.plus(Duration.ofMinutes(25)));   // still live
        clock.advance(Duration.ofMinutes(20));

        List<Long> ids = List.of(10L, 11L);
        when(cartRepository.lockCartsWithExpiredHolds(clock.instant(), 100)).thenReturn(ids);
        when(cartRepository.findByIdIn(ids)).thenReturn(List.of(alice, bob));
        when(inventoryRepository.release(1L, 3)).thenReturn(1);

        int processed = service.releaseNextBatch();

        assertThat(processed).isEqualTo(2);
        verify(inventoryRepository).release(1L, 3);                   // 2 (alice) + 1 (bob), one statement
        verify(inventoryRepository, never()).release(5L, 1);          // trainers hold has not expired
        assertThat(alice.getItems()).extracting(CartItem::getProductId).containsExactly(2L);
        assertThat(bob.getItems()).extracting(CartItem::getProductId).containsExactly(5L);
    }

    @Test
    void doesNothingWhenNoHoldHasExpired() {
        when(cartRepository.lockCartsWithExpiredHolds(T0, 100)).thenReturn(List.of());

        assertThat(service.releaseNextBatch()).isZero();
        verifyNoInteractions(inventoryRepository);
        verify(cartRepository, never()).findByIdIn(anyCollection());
    }

    @Test
    void neverReservesStock() {
        Cart alice = new Cart("alice", T0);
        alice.addItem(console, 1, T0, T0.plus(HOLD));
        clock.advance(HOLD);
        when(cartRepository.lockCartsWithExpiredHolds(clock.instant(), 100)).thenReturn(List.of(1L));
        when(cartRepository.findByIdIn(List.of(1L))).thenReturn(List.of(alice));
        when(inventoryRepository.release(1L, 1)).thenReturn(1);

        service.releaseNextBatch();

        verify(inventoryRepository, never()).tryReserve(anyLong(), anyInt());
    }
}
