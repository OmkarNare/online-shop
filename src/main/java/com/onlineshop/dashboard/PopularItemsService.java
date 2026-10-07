package com.onlineshop.dashboard;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.onlineshop.cart.CartItemRepository;
import com.onlineshop.catalog.Product;
import com.onlineshop.catalog.ProductRepository;
import com.onlineshop.catalog.ProductResponse;

@Service
public class PopularItemsService {

    private static final Logger log = LoggerFactory.getLogger(PopularItemsService.class);

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final DashboardProperties dashboardProperties;

    public PopularItemsService(StringRedisTemplate redisTemplate,
                               ProductRepository productRepository,
                               CartItemRepository cartItemRepository,
                               DashboardProperties dashboardProperties) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.dashboardProperties = dashboardProperties;
    }

    @Cacheable(cacheNames = "popularItems", key = "'all'", sync = true)
    public List<ProductResponse> getPopularItems() {
        // Fetch extra ids so inactive products can be skipped without leaving the list short.
        int candidateCount = dashboardProperties.maxItems() * 2;

        List<Long> productIds = getRankedProductIds(candidateCount);
        if (productIds.isEmpty()) {
            productIds = cartItemRepository.findTopProductIdsByCartQuantity(candidateCount);
        }
        return loadProductsInOrder(productIds);
    }

    private List<Long> getRankedProductIds(int count) {
        try {
            Set<String> productIds = redisTemplate.opsForZSet()
                    .reverseRange(PopularityKeys.RANKING, 0, count - 1L);
            if (productIds == null) {
                return List.of();
            }
            return productIds.stream()
                    .map(Long::valueOf)
                    .toList();
        } catch (DataAccessException e) {
            log.warn("Could not read popularity ranking from Redis, using database instead: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ProductResponse> loadProductsInOrder(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return productIds.stream()
                .map(productsById::get)
                .filter(Objects::nonNull)
                .filter(Product::isActive)
                .limit(dashboardProperties.maxItems())
                .map(ProductResponse::from)
                .toList();
    }
}
