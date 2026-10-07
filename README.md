# Online Shop API

Spring Boot 3.5 / Java 21 API for an online shop operating in London:
shopping cart with a **15-minute hold for high-demand items**, and the
high-traffic **popular items** and **new items** dashboards.
The liked-items and vouchers dashboards, checkout, and the production
deployment are specified in **[DESIGN.md](DESIGN.md)** (database, transactions,
API and distribution for everything).

## Stack
Java 21 · Spring Boot 3.5 (Web, Data JPA, Validation, Cache, Data Redis, Actuator) ·
PostgreSQL 16 + Flyway · Redis 7 · Caffeine · JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers

## Run locally
```bash
docker compose up -d                                   # PostgreSQL + Redis
mvn spring-boot:run -Dspring-boot.run.profiles=local   # 'local' loads 6 demo products
```

```bash
# Dashboards (public, cacheable)
curl -s localhost:8080/api/v1/dashboards/new?limit=5
curl -s localhost:8080/api/v1/dashboards/popular        # ranking refreshes every minute

# Cart (X-User-Id is set by the API gateway in production)
curl -s -X POST localhost:8080/api/v1/cart/items -H 'X-User-Id: alice' \
     -H 'Content-Type: application/json' -d '{"productId":1,"quantity":2}'   # high-demand: held 15 min
curl -s -X POST localhost:8080/api/v1/cart/items -H 'X-User-Id: alice' \
     -H 'Content-Type: application/json' -d '{"productId":4,"quantity":1}'   # regular item
curl -s -X PATCH localhost:8080/api/v1/cart/items/1 -H 'X-User-Id: alice' \
     -H 'Content-Type: application/json' -d '{"quantity":1}'
curl -s -X DELETE localhost:8080/api/v1/cart/items/1 -H 'X-User-Id: alice'
curl -s localhost:8080/api/v1/cart -H 'X-User-Id: alice'

# Back-office: create a product
curl -s -X POST localhost:8080/api/v1/products -H 'Content-Type: application/json' \
     -d '{"sku":"DROP-1","name":"Limited drop","category":"fashion","price":120.00,"initialStock":50,"highDemand":true}'
```

## Tests
```bash
mvn test      # unit + web-slice tests, no Docker needed
mvn verify    # also integration tests (*IT) on real PostgreSQL + Redis via Testcontainers (Docker required)
```
Integration tests include a 40-thread oversell test, a same-user concurrency
test, hold expiry with a controllable clock, and the dashboards against real Redis.

If Testcontainers cannot talk to Docker ("client version … is too old"), you are on
Docker Engine 29+: `src/test/resources/docker-java.properties` already pins
`api.version=1.44`; delete that file only if you run Docker Engine older than 25.

## Configuration (`application.yml`)
| Property | Default | Meaning |
|---|---|---|
| `shop.cart.hold-duration` | `PT15M` | How long a high-demand item stays in the cart |
| `shop.cart.max-quantity-per-item` | `10` | Per-item cap (anti-hoarding) |
| `shop.cart.expiry-sweep-interval` | `PT15S` | Background release of expired holds |
| `shop.dashboard.popularity-window-days` | `7` | Sliding window for "popular" |
| `shop.dashboard.popular-refresh-interval` | `PT1M` | Ranking snapshot rebuild |
| `spring.cache.caffeine.spec` | `expireAfterWrite=10s` | In-process dashboard cache |

## Code layout
```
com.onlineshop
├── catalog     Product, creation endpoint, ProductResponse (dashboard read model)
├── inventory   Inventory, atomic reserve/release SQL, StockPlan (netted, ordered stock changes)
├── cart        Cart aggregate, CartService (hold rules + transactions), expiry sweeper, REST API
├── dashboard   Popular (Redis ranking + DB fallback) and new items, Caffeine caching, REST API
├── config      Clock (Europe/London), caching, scheduling
└── web         Problem-detail error mapping
```
