package com.onlineshop.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockPlanTest {

    @Mock
    InventoryRepository inventoryRepository;

    @Test
    void netsChangesPerProduct() {
        StockPlan plan = new StockPlan()
                .release(7, 2)
                .reserve(7, 3)
                .reserve(3, 1)
                .release(5, 4)
                .reserve(5, 4);

        assertThat(plan.getNetChanges()).containsExactly(
                entry(3L, 1),
                entry(7L, 1));
    }

    @Test
    void appliesChangesInAscendingProductIdOrderToAvoidDeadlocks() {
        when(inventoryRepository.tryReserve(anyLong(), anyInt())).thenReturn(1);
        when(inventoryRepository.release(anyLong(), anyInt())).thenReturn(1);

        new StockPlan()
                .reserve(30, 1)
                .release(10, 2)
                .reserve(20, 5)
                .applyTo(inventoryRepository);

        InOrder order = inOrder(inventoryRepository);
        order.verify(inventoryRepository).release(10, 2);
        order.verify(inventoryRepository).tryReserve(20, 5);
        order.verify(inventoryRepository).tryReserve(30, 1);
    }

    @Test
    void failedReservationThrowsSoTheTransactionRollsBack() {
        when(inventoryRepository.tryReserve(1, 2)).thenReturn(0);

        StockPlan plan = new StockPlan().reserve(1, 2);

        assertThatThrownBy(() -> plan.applyTo(inventoryRepository))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("product 1");
    }

    @Test
    void releaseFollowedByEqualReserveTouchesNothing() {
        new StockPlan().release(9, 2).reserve(9, 2).applyTo(inventoryRepository);

        verify(inventoryRepository, never()).tryReserve(anyLong(), anyInt());
        verify(inventoryRepository, never()).release(anyLong(), anyInt());
    }
}
