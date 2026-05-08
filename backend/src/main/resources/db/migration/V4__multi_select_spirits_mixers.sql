-- V4: Support multiple spirits and mixers per custom drink order item
-- Replaces single spirit_item_id and mixer_item_id FKs on order_item
-- with join tables order_item_spirit and order_item_mixer

-- Create join table for spirits in a custom drink order item
CREATE TABLE order_item_spirit (
    id              BIGSERIAL       PRIMARY KEY,
    order_item_id   BIGINT          NOT NULL REFERENCES order_item(id) ON DELETE CASCADE,
    spirit_item_id  BIGINT          NOT NULL REFERENCES spirit_item(id)
);

CREATE INDEX idx_order_item_spirit_order_item_id ON order_item_spirit(order_item_id);
CREATE INDEX idx_order_item_spirit_spirit_item_id ON order_item_spirit(spirit_item_id);

-- Create join table for mixers in a custom drink order item
CREATE TABLE order_item_mixer (
    id              BIGSERIAL       PRIMARY KEY,
    order_item_id   BIGINT          NOT NULL REFERENCES order_item(id) ON DELETE CASCADE,
    mixer_item_id   BIGINT          NOT NULL REFERENCES mixer_item(id)
);

CREATE INDEX idx_order_item_mixer_order_item_id ON order_item_mixer(order_item_id);
CREATE INDEX idx_order_item_mixer_mixer_item_id ON order_item_mixer(mixer_item_id);

-- Remove old single-FK columns from order_item
ALTER TABLE order_item DROP COLUMN IF EXISTS spirit_item_id;
ALTER TABLE order_item DROP COLUMN IF EXISTS mixer_item_id;
