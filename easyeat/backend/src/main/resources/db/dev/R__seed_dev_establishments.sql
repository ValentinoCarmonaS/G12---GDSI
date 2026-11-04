-- Dev seed: the restaurants that the seeded staff users already point to.
-- R__seed_dev_users.sql gives staff a tenant_id, but V8 only creates three establishments, so an
-- administrator such as laura.admin@cafeparque.com had a tenant with no row in establishments and
-- every write of the operating configuration violated the foreign key.
-- The ids are the tenant_id values already used in R__seed_dev_users.sql.
INSERT INTO establishments (id, name, status, opening_time, closing_time, timezone)
VALUES
    ('1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'Café Parque',     'ACTIVE', '08:00:00', '23:00:00', 'America/Argentina/Buenos_Aires'),
    ('8bb12c8a-2d4e-4f76-a0b2-9a0910f13341', 'La Esquina',      'ACTIVE', '11:00:00', '23:00:00', 'America/Argentina/Buenos_Aires'),
    ('9cc23d9b-3e5f-4a87-b1c3-0b1a21e24452', 'Comedor Central', 'ACTIVE', '11:30:00', '22:30:00', 'America/Argentina/Buenos_Aires')
ON CONFLICT (id) DO NOTHING;
