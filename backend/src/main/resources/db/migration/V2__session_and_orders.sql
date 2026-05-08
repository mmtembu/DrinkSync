-- V2: Create SESSION, ORDERS, ORDER_ITEM, ORDER_STATE_HISTORY tables

CREATE TABLE session (
    id                BIGSERIAL       PRIMARY KEY,
    session_id        VARCHAR(36)     NOT NULL UNIQUE,
    station_id        BIGINT          NOT NULL REFERENCES station(id),
    last_activity_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    expired           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_session_id ON session(session_id);
CREATE INDEX idx_session_station_id ON session(station_id);

CREATE TABLE orders (
    id                    BIGSERIAL       PRIMARY KEY,
    station_id            BIGINT          NOT NULL REFERENCES station(id),
    session_id            BIGINT          REFERENCES session(id),
    visual_order_number   VARCHAR(20)     UNIQUE,
    state                 VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    queue_position        INTEGER,
    total_price           DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    pickup_window_start   TIMESTAMP,
    created_at            TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_station_id ON orders(station_id);
CREATE INDEX idx_orders_session_id ON orders(session_id);
CREATE INDEX idx_orders_state ON orders(state);
CREATE INDEX idx_orders_station_id_state ON orders(station_id, state);

CREATE TABLE order_item (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    item_type       VARCHAR(30)     NOT NULL,
    spirit_item_id  BIGINT          REFERENCES spirit_item(id),
    mixer_item_id   BIGINT          REFERENCES mixer_item(id),
    premade_item_id BIGINT          REFERENCES premade_item(id),
    cup_option      VARCHAR(20),
    cup_price       DECIMAL(10, 2),
    quantity        INTEGER         NOT NULL CHECK (quantity >= 1),
    unit_price      DECIMAL(10, 2)  NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_item_order_id ON order_item(order_id);

CREATE TABLE order_state_history (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_state      VARCHAR(30),
    to_state        VARCHAR(30)     NOT NULL,
    triggered_by    VARCHAR(20)     NOT NULL,
    transitioned_at TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_state_history_order_id ON order_state_history(order_id);
