-- V7: Add WhatsApp notification columns to session table for phone persistence across orders

ALTER TABLE session ADD COLUMN customer_phone VARCHAR(20);
ALTER TABLE session ADD COLUMN whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE;
