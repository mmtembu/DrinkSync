-- V1: Create STATION, SPIRIT_ITEM, MIXER_ITEM, PREMADE_ITEM tables

CREATE TABLE station (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    location_description VARCHAR(500),
    cup_price   DECIMAL(10, 2)  NOT NULL DEFAULT 0
                                CHECK (cup_price >= 0),
    access_code VARCHAR(6)      NOT NULL UNIQUE,
    pickup_window_minutes INTEGER NOT NULL DEFAULT 10
                                CHECK (pickup_window_minutes >= 5 AND pickup_window_minutes <= 30),
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE spirit_item (
    id          BIGSERIAL       PRIMARY KEY,
    station_id  BIGINT          NOT NULL REFERENCES station(id),
    name        VARCHAR(255)    NOT NULL,
    price       DECIMAL(10, 2)  NOT NULL CHECK (price > 0),
    available   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_spirit_item_station_id ON spirit_item(station_id);

CREATE TABLE mixer_item (
    id          BIGSERIAL       PRIMARY KEY,
    station_id  BIGINT          NOT NULL REFERENCES station(id),
    name        VARCHAR(255)    NOT NULL,
    price       DECIMAL(10, 2)  NOT NULL CHECK (price > 0),
    available   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mixer_item_station_id ON mixer_item(station_id);

CREATE TABLE premade_item (
    id          BIGSERIAL       PRIMARY KEY,
    station_id  BIGINT          NOT NULL REFERENCES station(id),
    name        VARCHAR(255)    NOT NULL,
    description VARCHAR(1000)   NOT NULL,
    price       DECIMAL(10, 2)  NOT NULL CHECK (price > 0),
    available   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_premade_item_station_id ON premade_item(station_id);
