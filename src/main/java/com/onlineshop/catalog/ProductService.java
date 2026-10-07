package com.onlineshop.catalog;

import java.time.Clock;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlineshop.inventory.Inventory;
import com.onlineshop.inventory.InventoryRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    public ProductService(ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          Clock clock) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    @Transactional
    @CacheEvict(cacheNames = "newItems", allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product(
                request.sku(),
                request.name(),
                request.description(),
                request.category(),
                request.price(),
                request.highDemand(),
                clock.instant());
        Product savedProduct = productRepository.save(product);
        inventoryRepository.save(new Inventory(savedProduct.getId(), request.initialStock()));
        return ProductResponse.from(savedProduct);
    }
}
