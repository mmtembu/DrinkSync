-- V3: Create IDEMPOTENCY_KEY, AUTH_ATTEMPT tables

CREATE TABLE idempotency_key (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    idempotency_key UUID            NOT NULL UNIQUE,
    response_body   TEXT,
    response_status INTEGER,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP       NOT NULL DEFAULT (NOW() + INTERVAL '24 hours')
);

CREATE INDEX idx_idempotency_key_order_id_key ON idempotency_key(order_id, idempotency_key);

CREATE TABLE auth_attempt (
    id           BIGSERIAL       PRIMARY KEY,
    station_id   BIGINT          NOT NULL REFERENCES station(id),
    success      BOOLEAN         NOT NULL,
    attempted_at TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_attempt_station_id_attempted_at ON auth_attempt(station_id, attempted_at);
