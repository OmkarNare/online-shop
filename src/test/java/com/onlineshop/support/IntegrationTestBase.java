package com.onlineshop.support;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.onlineshop.catalog.CreateProductRequest;
import com.onlineshop.catalog.ProductService;

/**
 * Real PostgreSQL and Redis in Docker (Testcontainers). Containers are started
 * once per JVM and the Spring context is shared by all integration tests;
 * state is wiped before each test. Scheduled jobs are off and caches are
 * disabled, so tests trigger jobs explicitly and always see fresh data.
 */
@SpringBootTest(properties = {
        "shop.scheduling.enabled=false",
        "spring.cache.type=none"
})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestClockConfig.class)
public abstract class IntegrationTestBase {

    public static final Instant START = Instant.parse("2026-10-01T09:00:00Z");

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected MutableClock clock;
    @Autowired protected ProductService productService;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE cart_item, cart, inventory, product RESTART IDENTITY CASCADE");
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        clock.set(START);
    }

    protected long createProduct(String sku, boolean highDemand, int stock) {
        return productService.createProduct(new CreateProductRequest(
                sku, sku + " name", null, "test", new BigDecimal("10.00"), stock, highDemand)).id();
    }

    protected int reserved(long productId) {
        return jdbc.queryForObject("SELECT reserved FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    protected int cartItemRows() {
        return jdbc.queryForObject("SELECT count(*) FROM cart_item", Integer.class);
    }
}
