-- V5: Seed demo data for development and testing

-- Station 1: Main Bar
INSERT INTO station (id, name, location_description, cup_price, access_code, pickup_window_minutes, created_at)
VALUES (1, 'Main Bar', 'Near the main entrance', 5.00, 'BAR001', 10, NOW());

-- Station 2: VIP Lounge
INSERT INTO station (id, name, location_description, cup_price, access_code, pickup_window_minutes, created_at)
VALUES (2, 'VIP Lounge', 'Upstairs, left wing', 10.00, 'VIP002', 15, NOW());

-- Spirits for Station 1 (Main Bar)
INSERT INTO spirit_item (station_id, name, price, available, created_at) VALUES
(1, 'Vodka', 25.00, true, NOW()),
(1, 'Gin', 30.00, true, NOW()),
(1, 'Whiskey', 35.00, true, NOW()),
(1, 'Rum', 25.00, true, NOW()),
(1, 'Tequila', 30.00, true, NOW()),
(1, 'Spirit', 40.00, true, NOW());

-- Mixers for Station 1 (Main Bar)
INSERT INTO mixer_item (station_id, name, price, available, created_at) VALUES
(1, 'Coca-Cola', 10.00, true, NOW()),
(1, 'Lemonade', 10.00, true, NOW()),
(1, 'Tonic Water', 12.00, true, NOW()),
(1, 'Ginger Ale', 10.00, true, NOW()),
(1, 'Red Bull', 20.00, true, NOW());

-- Premade items for Station 1 (Main Bar)
INSERT INTO premade_item (station_id, name, description, price, available, created_at) VALUES
(1, 'Castle Lager', 'Classic South African lager, 340ml can', 20.00, true, NOW()),
(1, 'Savanna Dry', 'Premium cider, 330ml bottle', 25.00, true, NOW()),
(1, 'Bottled Water', 'Still water, 500ml', 10.00, true, NOW()),
(1, 'Heineken', 'Imported lager, 330ml can', 30.00, true, NOW());

-- Spirits for Station 2 (VIP Lounge)
INSERT INTO spirit_item (station_id, name, price, available, created_at) VALUES
(2, 'Grey Goose Vodka', 50.00, true, NOW()),
(2, 'Hendricks Gin', 55.00, true, NOW()),
(2, 'Johnnie Walker Black', 60.00, true, NOW()),
(2, 'Patron Tequila', 65.00, true, NOW());

-- Mixers for Station 2 (VIP Lounge)
INSERT INTO mixer_item (station_id, name, price, available, created_at) VALUES
(2, 'Fever-Tree Tonic', 18.00, true, NOW()),
(2, 'Fresh Lime Soda', 15.00, true, NOW()),
(2, 'Ginger Beer', 18.00, true, NOW()),
(2, 'Red Bull', 25.00, true, NOW());

-- Premade items for Station 2 (VIP Lounge)
INSERT INTO premade_item (station_id, name, description, price, available, created_at) VALUES
(2, 'Moët & Chandon Mini', 'Champagne, 200ml bottle', 150.00, true, NOW()),
(2, 'Peroni', 'Italian lager, 330ml bottle', 40.00, true, NOW()),
(2, 'Sparkling Water', 'San Pellegrino, 500ml', 20.00, true, NOW());

-- Reset sequences to avoid ID conflicts with future inserts
SELECT setval('station_id_seq', (SELECT MAX(id) FROM station));
SELECT setval('spirit_item_id_seq', (SELECT MAX(id) FROM spirit_item));
SELECT setval('mixer_item_id_seq', (SELECT MAX(id) FROM mixer_item));
SELECT setval('premade_item_id_seq', (SELECT MAX(id) FROM premade_item));
