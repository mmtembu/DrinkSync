-- V6: Add WhatsApp notification columns to orders table
-- Supports customer phone collection, opt-in preference, and message status tracking

ALTER TABLE orders ADD COLUMN customer_phone VARCHAR(20);
ALTER TABLE orders ADD COLUMN whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN whatsapp_message_status VARCHAR(20);
