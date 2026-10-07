package com.onlineshop.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlineshop.catalog.Product;
import com.onlineshop.catalog.ProductNotFoundException;
import com.onlineshop.catalog.ProductRepository;
import com.onlineshop.inventory.InsufficientStockException;
import com.onlineshop.inventory.InventoryRepository;
import com.onlineshop.support.MutableClock;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    static final Duration HOLD = Duration.ofMinutes(15);

    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    MutableClock clock = new MutableClock(T0);
    CartService service;

    Cart aliceCart;
    Product console;   // high demand -> held for 15 minutes
    Product mug;       // regular item -> never held

    @BeforeEach
    void setUp() {
        CartProperties properties = new CartProperties(HOLD, 10, Duration.ofSeconds(15), 100);
        service = new CartService(cartRepository, productRepository, inventoryRepository, eventPublisher, properties, clock);
        aliceCart = new Cart("alice", T0);
        console = product(1L, "CONSOLE", true, "499.99");
        mug = product(2L, "MUG", false, "9.50");
    }

    @Nested
    class AddingHighDemandItems {

        @Test
        void reservesStockAndStartsFifteenMinuteHold() {
            cartExists();
            productExists(console);
            when(inventoryRepository.tryReserve(1L, 2)).thenReturn(1);

            CartResponse view = service.addItem("alice", 1L, 2);

            verify(inventoryRepository).tryReserve(1L, 2);
            assertThat(view.items()).singleElement().satisfies(line -> {
                assertThat(line.quantity()).isEqualTo(2);
                assertThat(line.reservedUntil()).isEqualTo(T0.plus(HOLD));
                assertThat(line.secondsRemaining()).isEqualTo(900L);
                assertThat(line.lineTotal()).isEqualByComparingTo("999.98");
            });
            verify(eventPublisher).publishEvent(new ItemAddedToCartEvent(1L, 2));
        }

        @Test
        void failsWhenSoldOutAndPublishesNothing() {
            cartExists();
            productExists(console);
            when(inventoryRepository.tryReserve(1L, 1)).thenReturn(0);

            assertThatThrownBy(() -> service.addItem("alice", 1L, 1))
                    .isInstanceOf(InsufficientStockException.class);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void addingMoreReservesOnlyTheDifferenceAndKeepsTheOriginalDeadline() {
            cartExists();
            productExists(console);
            when(inventoryRepository.tryReserve(eq(1L), anyInt())).thenReturn(1);

            service.addItem("alice", 1L, 1);
            clock.advance(Duration.ofMinutes(5));
            CartResponse view = service.addItem("alice", 1L, 2);

            verify(inventoryRepository).tryReserve(1L, 1);
            verify(inventoryRepository).tryReserve(1L, 2);
            assertThat(view.items()).singleElement().satisfies(line -> {
                assertThat(line.quantity()).isEqualTo(3);
                assertThat(line.reservedUntil()).isEqualTo(T0.plus(HOLD));   // not extended to T0+20m
            });
        }

        @Test
        void reAddingAnExpiredItemStartsANewHoldWithoutTouchingStock() {
            aliceCart.addItem(console, 2, T0, T0.plus(HOLD));
            clock.advance(Duration.ofMinutes(16));
            cartExists();
            productExists(console);

            CartResponse view = service.addItem("alice", 1L, 2);

            // release 2 (expired) + reserve 2 (re-add) nets to zero: no stock UPDATE at all
            verify(inventoryRepository, never()).tryReserve(anyLong(), anyInt());
            verify(inventoryRepository, never()).release(anyLong(), anyInt());
            verify(cartRepository).flush();   // expired row deleted before the new row is inserted
            assertThat(view.items()).singleElement()
                    .satisfies(line -> assertThat(line.reservedUntil()).isEqualTo(clock.instant().plus(HOLD)));
        }
    }

    @Nested
    class AddingRegularItems {

        @Test
        void checksAvailabilityButReservesNothing() {
            cartExists();
            productExists(mug);
            when(inventoryRepository.findAvailableQuantity(2L)).thenReturn(Optional.of(100));

            CartResponse view = service.addItem("alice", 2L, 3);

            verify(inventoryRepository, never()).tryReserve(anyLong(), anyInt());
            assertThat(view.items()).singleElement().satisfies(line -> {
                assertThat(line.quantity()).isEqualTo(3);
                assertThat(line.reservedUntil()).isNull();
                assertThat(line.secondsRemaining()).isNull();
            });
        }

        @Test
        void failsWhenMoreThanAvailableIsRequested() {
            cartExists();
            productExists(mug);
            when(inventoryRepository.findAvailableQuantity(2L)).thenReturn(Optional.of(2));

            assertThatThrownBy(() -> service.addItem("alice", 2L, 3))
                    .isInstanceOf(InsufficientStockException.class);
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsQuantityAboveThePerItemLimit() {
            cartExists();
            productExists(mug);

            assertThatThrownBy(() -> service.addItem("alice", 2L, 11))
                    .isInstanceOf(QuantityLimitExceededException.class);
        }

        @Test
        void rejectsUnknownProductBeforeTouchingTheCart() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addItem("alice", 99L, 1))
                    .isInstanceOf(ProductNotFoundException.class);
            verifyNoInteractions(cartRepository, inventoryRepository);
        }

        @Test
        void rejectsNonPositiveQuantity() {
            assertThatThrownBy(() -> service.addItem("alice", 1L, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void createsTheCartOnFirstUse() {
        when(cartRepository.findByUserIdForUpdate("bob"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new Cart("bob", T0)));
        productExists(mug);
        when(inventoryRepository.findAvailableQuantity(2L)).thenReturn(Optional.of(10));

        service.addItem("bob", 2L, 1);

        verify(cartRepository).insertIfAbsent("bob", T0);
    }

    @Nested
    class UpdatingQuantity {

        @Test
        void loweringAHeldItemReleasesTheDifference() {
            aliceCart.addItem(console, 3, T0, T0.plus(HOLD));
            cartExists();
            when(inventoryRepository.release(1L, 2)).thenReturn(1);

            CartResponse view = service.updateItemQuantity("alice", 1L, 1);

            verify(inventoryRepository).release(1L, 2);
            assertThat(view.items()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(1));
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void raisingAHeldItemReservesTheDifference() {
            aliceCart.addItem(console, 1, T0, T0.plus(HOLD));
            cartExists();
            when(inventoryRepository.tryReserve(1L, 3)).thenReturn(1);

            service.updateItemQuantity("alice", 1L, 4);

            verify(inventoryRepository).tryReserve(1L, 3);
            verify(eventPublisher).publishEvent(new ItemAddedToCartEvent(1L, 3));
        }

        @Test
        void failsForAnItemWhoseHoldHasExpired() {
            aliceCart.addItem(console, 1, T0, T0.plus(HOLD));
            clock.advance(HOLD);
            cartExists();

            assertThatThrownBy(() -> service.updateItemQuantity("alice", 1L, 2))
                    .isInstanceOf(CartItemNotFoundException.class);
        }
    }

    @Nested
    class RemovingItems {

        @Test
        void removingAHeldItemReleasesItsStock() {
            aliceCart.addItem(console, 2, T0, T0.plus(HOLD));
            cartExists();
            when(inventoryRepository.release(1L, 2)).thenReturn(1);

            CartResponse view = service.removeItem("alice", 1L);

            verify(inventoryRepository).release(1L, 2);
            assertThat(view.items()).isEmpty();
        }

        @Test
        void removingARegularItemReleasesNothing() {
            aliceCart.addItem(mug, 2, T0, null);
            cartExists();

            CartResponse view = service.removeItem("alice", 2L);

            verify(inventoryRepository, never()).release(anyLong(), anyInt());
            assertThat(view.items()).isEmpty();
        }

        @Test
        void removingFromANonExistentCartIsANoOp() {
            when(cartRepository.findByUserIdForUpdate("alice")).thenReturn(Optional.empty());

            CartResponse view = service.removeItem("alice", 1L);

            assertThat(view.items()).isEmpty();
            verifyNoInteractions(inventoryRepository);
        }
    }

    @Nested
    class Expiry {

        @Test
        void expiredHoldIsReleasedAndRemovedOnTheUsersNextChange() {
            aliceCart.addItem(console, 2, T0, T0.plus(HOLD));
            clock.advance(Duration.ofMinutes(16));
            cartExists();
            productExists(mug);
            when(inventoryRepository.findAvailableQuantity(2L)).thenReturn(Optional.of(10));
            when(inventoryRepository.release(1L, 2)).thenReturn(1);

            CartResponse view = service.addItem("alice", 2L, 1);

            verify(inventoryRepository).release(1L, 2);
            assertThat(view.items()).extracting(CartItemResponse::productId).containsExactly(2L);
        }

        @Test
        void readingTheCartHidesExpiredItemsImmediately() {
            aliceCart.addItem(console, 1, T0, T0.plus(HOLD));
            aliceCart.addItem(mug, 1, T0, null);
            clock.advance(HOLD);   // exactly at the deadline counts as expired
            when(cartRepository.findByUserId("alice")).thenReturn(Optional.of(aliceCart));

            CartResponse view = service.getCart("alice");

            assertThat(view.items()).extracting(CartItemResponse::productId).containsExactly(2L);
            assertThat(view.total()).isEqualByComparingTo("9.50");
            verifyNoInteractions(inventoryRepository);
        }
    }

    @Test
    void readingAMissingCartReturnsAnEmptyCart() {
        when(cartRepository.findByUserId(any())).thenReturn(Optional.empty());

        assertThat(service.getCart("nobody").items()).isEmpty();
    }

    private void cartExists() {
        when(cartRepository.findByUserIdForUpdate("alice")).thenReturn(Optional.of(aliceCart));
    }

    private void productExists(Product product) {
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    }

    static Product product(long id, String sku, boolean highDemand, String price) {
        Product product = new Product(sku, sku + " name", null, "test", new BigDecimal(price), highDemand, T0);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
