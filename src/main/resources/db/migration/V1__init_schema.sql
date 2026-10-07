-- ---------------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------------
CREATE TABLE product (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku          VARCHAR(64)    NOT NULL UNIQUE,
    name         VARCHAR(255)   NOT NULL,
    description  VARCHAR(2000),
    category     VARCHAR(100)   NOT NULL,
    price        NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    currency     VARCHAR(3)     NOT NULL DEFAULT 'GBP',
    -- High-demand items are held in the cart for a limited time (15 min by default)
    high_demand  BOOLEAN        NOT NULL DEFAULT FALSE,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ    NOT NULL
);

-- "New items" dashboard: newest active products first
CREATE INDEX idx_product_new_items ON product (created_at DESC, id DESC) WHERE active;

-- ---------------------------------------------------------------------------
-- Inventory: kept out of the product row so hot stock updates do not
-- contend with (or bloat) catalog reads.
-- ---------------------------------------------------------------------------
CREATE TABLE inventory (
    product_id  BIGINT  PRIMARY KEY REFERENCES product (id),
    on_hand     INTEGER NOT NULL CHECK (on_hand >= 0),
    reserved    INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_inventory_reserved CHECK (reserved >= 0 AND reserved <= on_hand)
);

-- ---------------------------------------------------------------------------
-- Shopping cart: one active cart per user. The cart row is the lock that
-- serialises every change to one user's cart (including hold expiry).
-- ---------------------------------------------------------------------------
CREATE TABLE cart (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL UNIQUE,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE cart_item (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id         BIGINT      NOT NULL REFERENCES cart (id) ON DELETE CASCADE,
    product_id      BIGINT      NOT NULL REFERENCES product (id),
    quantity        INTEGER     NOT NULL CHECK (quantity > 0),
    added_at        TIMESTAMPTZ NOT NULL,
    -- NULL  -> normal line, no stock held
    -- value -> `quantity` units are counted in inventory.reserved until this instant
    reserved_until  TIMESTAMPTZ,
    CONSTRAINT uq_cart_item_product UNIQUE (cart_id, product_id)
);

-- Expiry sweeper only scans held lines
CREATE INDEX idx_cart_item_hold_expiry ON cart_item (reserved_until) WHERE reserved_until IS NOT NULL;
-- Fallback "popular" query when Redis is unavailable
CREATE INDEX idx_cart_item_product ON cart_item (product_id);
