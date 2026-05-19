-- V8: Create notification_log table for WhatsApp notification tracking

CREATE TABLE notification_log (
    id                  BIGSERIAL       PRIMARY KEY,
    order_id            BIGINT          NOT NULL REFERENCES orders(id),
    channel             VARCHAR(20)     NOT NULL,
    message_type        VARCHAR(30)     NOT NULL,
    destination         VARCHAR(20)     NOT NULL,
    provider_message_id VARCHAR(100),
    status              VARCHAR(20)     NOT NULL DEFAULT 'pending',
    error_message       TEXT,
    sent_at             TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_log_order_id ON notification_log(order_id);

CREATE INDEX idx_notification_log_provider_msg_id ON notification_log(provider_message_id);

-- Unique partial index for deduplication: prevents duplicate successful notifications
-- for the same order and message type
CREATE UNIQUE INDEX idx_notification_log_dedup
    ON notification_log(order_id, message_type)
    WHERE status IN ('sent', 'delivered', 'read');
