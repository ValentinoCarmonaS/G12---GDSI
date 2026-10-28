-- 0. Enable unaccent extension for diacritic-insensitive search
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Allow unaccent in functional index expressions by marking it IMMUTABLE
ALTER FUNCTION unaccent(text) IMMUTABLE;

-- 1. Create establishments table
CREATE TABLE IF NOT EXISTS establishments (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(150) NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    opening_time TIME         NOT NULL DEFAULT '11:00:00',
    closing_time TIME         NOT NULL DEFAULT '23:00:00',
    timezone     VARCHAR(50)  NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_establishment_status CHECK (status IN ('ACTIVE', 'PAUSED', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_establishments_search
    ON establishments(status, unaccent(LOWER(name)));

-- 2. Seed development establishments
INSERT INTO establishments (id, name, status, opening_time, closing_time, timezone)
VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Café Central Palermo', 'ACTIVE', '08:00:00', '23:30:00', 'America/Argentina/Buenos_Aires'),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Trattoria Bella Vista', 'ACTIVE', '11:30:00', '23:00:00', 'America/Argentina/Buenos_Aires'),
    ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'Bistró San Telmo', 'PAUSED', '12:00:00', '22:00:00', 'America/Argentina/Buenos_Aires')
ON CONFLICT (id) DO NOTHING;
