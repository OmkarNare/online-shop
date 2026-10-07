package com.onlineshop.inventory;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends Repository<Inventory, Long> {

    Inventory save(Inventory inventory);

    /**
     * Check and update happen in one statement, so two concurrent requests
     * can never both take the last unit.
     *
     * @return 1 if the stock was reserved, 0 if there was not enough
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
            SET reserved = reserved + :quantity
            WHERE product_id = :productId
              AND on_hand - reserved >= :quantity
            """, nativeQuery = true)
    int tryReserve(@Param("productId") long productId, @Param("quantity") int quantity);

    /**
     * @return 1 if the stock was released, 0 if less than {@code quantity} was reserved
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
            SET reserved = reserved - :quantity
            WHERE product_id = :productId
              AND reserved >= :quantity
            """, nativeQuery = true)
    int release(@Param("productId") long productId, @Param("quantity") int quantity);

    @Query(value = "SELECT on_hand - reserved FROM inventory WHERE product_id = :productId",
            nativeQuery = true)
    Optional<Integer> findAvailableQuantity(@Param("productId") long productId);
}
