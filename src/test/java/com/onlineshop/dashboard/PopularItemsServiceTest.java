package com.onlineshop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlineshop.cart.CartItemRepository;
import com.onlineshop.catalog.Product;
import com.onlineshop.catalog.ProductRepository;
import com.onlineshop.catalog.ProductResponse;

@ExtendWith(MockitoExtension.class)
class PopularItemsServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ZSetOperations<String, String> zset;
    @Mock ProductRepository productRepository;
    @Mock CartItemRepository cartItemRepository;

    PopularItemsService service;

    @BeforeEach
    void setUp() {
        service = new PopularItemsService(redisTemplate, productRepository, cartItemRepository,
                new DashboardProperties(50, 7, Duration.ofMinutes(1)));
        when(redisTemplate.opsForZSet()).thenReturn(zset);
    }

    @Test
    void returnsProductsInRankingOrderAndSkipsInactiveOnes() {
        when(zset.reverseRange(PopularityKeys.RANKING, 0, 99))
                .thenReturn(new LinkedHashSet<>(List.of("3", "1", "2")));
        // the database returns rows in arbitrary order; ranking order must win
        when(productRepository.findAllById(List.of(3L, 1L, 2L)))
                .thenReturn(List.of(product(1, true), product(2, false), product(3, true)));

        List<ProductResponse> popular = service.getPopularItems();

        assertThat(popular).extracting(ProductResponse::id).containsExactly(3L, 1L);
        verify(cartItemRepository, never()).findTopProductIdsByCartQuantity(anyInt());
    }

    @Test
    void fallsBackToTheDatabaseWhenRedisIsDown() {
        when(zset.reverseRange(PopularityKeys.RANKING, 0, 99))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        when(cartItemRepository.findTopProductIdsByCartQuantity(100)).thenReturn(List.of(2L));
        when(productRepository.findAllById(List.of(2L))).thenReturn(List.of(product(2, true)));

        assertThat(service.getPopularItems()).extracting(ProductResponse::id).containsExactly(2L);
    }

    @Test
    void fallsBackToTheDatabaseWhenThereIsNoRankingYet() {
        when(zset.reverseRange(PopularityKeys.RANKING, 0, 99)).thenReturn(new LinkedHashSet<>());
        when(cartItemRepository.findTopProductIdsByCartQuantity(100)).thenReturn(List.of());

        assertThat(service.getPopularItems()).isEmpty();
    }

    private static Product product(long id, boolean active) {
        Product product = new Product("SKU-" + id, "Product " + id, null, "test",
                new BigDecimal("10.00"), false, Instant.parse("2026-10-01T10:00:00Z"));
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "active", active);
        return product;
    }
}
