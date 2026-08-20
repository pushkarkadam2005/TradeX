CREATE TABLE orders (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID           NOT NULL REFERENCES users(id),
    stock_id           UUID           NOT NULL REFERENCES stocks(id),
    symbol             VARCHAR(10)    NOT NULL,
    side               VARCHAR(10)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type         VARCHAR(10)    NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT')),
    quantity           BIGINT         NOT NULL CHECK (quantity > 0),
    remaining_quantity BIGINT         NOT NULL CHECK (remaining_quantity >= 0 AND remaining_quantity <= quantity),
    limit_price        NUMERIC(19,4)  CHECK (
                                        (order_type = 'LIMIT' AND limit_price IS NOT NULL AND limit_price > 0) OR
                                        (order_type = 'MARKET' AND limit_price IS NULL)
                                      ),
    status             VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                                      CHECK (status IN ('PENDING', 'OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED')),
    client_order_id    VARCHAR(64)    NOT NULL,
    order_sequence     BIGINT         NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_client_order_id UNIQUE (user_id, client_order_id),
    CONSTRAINT uk_orders_order_sequence UNIQUE (order_sequence)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_symbol_status ON orders(symbol, status);
CREATE INDEX idx_orders_sequence ON orders(order_sequence ASC);

CREATE TABLE trades (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id  VARCHAR(36)    NOT NULL UNIQUE,
    buy_order_id  UUID           NOT NULL REFERENCES orders(id),
    sell_order_id UUID           NOT NULL REFERENCES orders(id),
    stock_id      UUID           NOT NULL REFERENCES stocks(id),
    buyer_id      UUID           NOT NULL REFERENCES users(id),
    seller_id     UUID           NOT NULL REFERENCES users(id),
    symbol        VARCHAR(10)    NOT NULL,
    price         NUMERIC(19,4)  NOT NULL CHECK (price > 0),
    quantity      BIGINT         NOT NULL CHECK (quantity > 0),
    version       BIGINT         NOT NULL DEFAULT 0,
    executed_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trades_buy_order_id  ON trades(buy_order_id);
CREATE INDEX idx_trades_sell_order_id ON trades(sell_order_id);
CREATE INDEX idx_trades_symbol        ON trades(symbol);
CREATE INDEX idx_trades_buyer_id      ON trades(buyer_id);
CREATE INDEX idx_trades_seller_id     ON trades(seller_id);
