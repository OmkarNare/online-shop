package com.onlineshop.catalog;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Newest active products first; served by the partial index idx_product_new_items. */
    List<Product> findByActiveTrueOrderByCreatedAtDescIdDesc(Limit limit);
}
