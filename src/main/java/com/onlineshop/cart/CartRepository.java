package com.onlineshop.cart;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // Safe when two first requests of the same user arrive at the same time.
    @Modifying
    @Query(value = """
            INSERT INTO cart (user_id, updated_at)
            VALUES (:userId, :now)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") String userId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByUserId(String userId);

    /**
     * Locks up to {@code batchSize} carts that have an expired hold. SKIP LOCKED
     * skips carts that a user request is currently changing, and lets several
     * instances run the expiry job at the same time.
     */
    @Query(value = """
            SELECT c.id
            FROM cart c
            WHERE EXISTS (SELECT 1
                          FROM cart_item ci
                          WHERE ci.cart_id = c.id
                            AND ci.reserved_until <= :now)
            ORDER BY c.id
            LIMIT :batchSize
            FOR NO KEY UPDATE OF c SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockCartsWithExpiredHolds(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @EntityGraph(attributePaths = "items")
    List<Cart> findByIdIn(Collection<Long> ids);
}
