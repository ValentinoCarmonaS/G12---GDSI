-- Dev seed: Café Parque is the sole active restaurant with standard operating hours (08:00:00 to 23:00:00)
-- All other establishments are DISABLED to ensure clean single-tenant diner discovery
INSERT INTO establishments (id, name, status, opening_time, closing_time, timezone)
VALUES
    ('1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'Café Parque',     'ACTIVE',   '08:00:00', '23:00:00', 'America/Argentina/Buenos_Aires'),
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Café Central Palermo', 'DISABLED', '08:00:00', '23:30:00', 'America/Argentina/Buenos_Aires'),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Trattoria Bella Vista', 'DISABLED', '11:30:00', '23:00:00', 'America/Argentina/Buenos_Aires'),
    ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'Bistró San Telmo',     'DISABLED', '12:00:00', '22:00:00', 'America/Argentina/Buenos_Aires'),
    ('8bb12c8a-2d4e-4f76-a0b2-9a0910f13341', 'La Esquina',          'DISABLED', '11:00:00', '23:00:00', 'America/Argentina/Buenos_Aires'),
    ('9cc23d9b-3e5f-4a87-b1c3-0b1a21e24452', 'Comedor Central',     'DISABLED', '11:30:00', '22:30:00', 'America/Argentina/Buenos_Aires')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    opening_time = EXCLUDED.opening_time,
    closing_time = EXCLUDED.closing_time,
    timezone = EXCLUDED.timezone,
    updated_at = NOW();

-- Operating schedules: standard weekly schedule (08:00:00 to 23:00:00) for Café Parque
INSERT INTO establishment_operating_schedules (id, establishment_id, day_of_week, opening_time, closing_time, is_closed, version, created_at, updated_at)
VALUES 
    ('e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'MONDAY',    '08:00:00', '23:00:00', false, 0, NOW(), NOW()),
    ('e2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'TUESDAY',   '08:00:00', '23:00:00', false, 0, NOW(), NOW()),
    ('e3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'WEDNESDAY', '08:00:00', '23:00:00', false, 0, NOW(), NOW()),
    ('e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'THURSDAY',  '08:00:00', '23:00:00', false, 0, NOW(), NOW()),
    ('e5eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'FRIDAY',    '08:00:00', '23:00:00', false, 0, NOW(), NOW()),
    ('e6eebc99-9c0b-4ef8-bb6d-6bb9bd380a06', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'SATURDAY',  '08:00:00', '23:00:00', false, 0, NOW(), NOW()),
    ('e7eebc99-9c0b-4ef8-bb6d-6bb9bd380a07', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'SUNDAY',    '08:00:00', '23:00:00', false, 0, NOW(), NOW())
ON CONFLICT (establishment_id, day_of_week) DO UPDATE SET 
    opening_time = EXCLUDED.opening_time, 
    closing_time = EXCLUDED.closing_time, 
    is_closed = EXCLUDED.is_closed, 
    updated_at = NOW();
