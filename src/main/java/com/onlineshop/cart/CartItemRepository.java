package com.onlineshop.cart;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends Repository<CartItem, Long> {

    // Used for the popular dashboard when Redis is not available.
    @Query(value = """
            SELECT product_id
            FROM cart_item
            GROUP BY product_id
            ORDER BY SUM(quantity) DESC, product_id
            LIMIT :maxResults
            """, nativeQuery = true)
    List<Long> findTopProductIdsByCartQuantity(@Param("maxResults") int maxResults);
}
