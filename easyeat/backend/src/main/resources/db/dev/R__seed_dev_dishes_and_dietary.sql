-- ====================================================================
-- EasyEat: Dev Profile Repeatable Seed - Dishes & Dietary Mapping
-- Establishment Tenant: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 (Admin synthetic tenant)
-- ====================================================================

-- 0. Ensure Synthetic Admin user exists for dishes created_by foreign key
INSERT INTO users (
    id,
    email,
    password_hash,
    role,
    status,
    tenant_id,
    token_version,
    created_at,
    updated_at
) VALUES (
    'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
    'admin@easyeat.com',
    '$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2',
    'ADMINISTRADOR',
    'ACTIVE',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    0,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 1. Insert Sample Dishes
--
-- retail_price and published_at (spec 007) are stated explicitly as NULL rather
-- than omitted. They are not decoration: V8's backfill copies price into
-- retail_price for rows already present on a developer's existing database, so
-- without these the five dishes would come out PRICED there and UNPRICED on a
-- fresh clone. Naming them -- here and in the DO UPDATE below -- makes the dev
-- state identical either way, and makes re-seeding return it to that state.
--
-- status stays PENDING_PRICE, owned by spec 005. Spec 007's review inbox exists
-- to decide dishes that are awaiting a decision, and its quickstart approves one
-- of these, so pending is the fixture it needs. Decision D5, which would have
-- published them, is withdrawn: see specs/007-admin-dish-approval-pricing/
-- research.md section 3.6.
INSERT INTO dishes (id, tenant_id, name, description, category, price, retail_price, published_at, status, allergens_confirmed, created_by, is_active, created_at, updated_at)
VALUES
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Ensalada Mediterránea', 'Mix de hojas verdes, tomates secos, queso feta y aceitunas negras.', 'Ensaladas', 4500.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW()),
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Pad Thai con Maní', 'Fideos de arroz salteados con verduras, salsa de soja y maní tostado picado.', 'Platos principales', 6200.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW()),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Hamburguesa Clásica con Queso', 'Medallón de carne vacuna, queso cheddar y pan brioche artesanal.', 'Platos principales', 7500.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW()),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Bowl Vegano Proteico', 'Quinoa, tofu crocante, palta, espinaca baby y aderezo de tahini.', 'Platos principales', 5800.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW()),
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Salmón Grillado con Espárragos', 'Filete de salmón rosado a la plancha acompañado de espárragos y limón.', 'Platos principales', 9500.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    category = EXCLUDED.category,
    status = EXCLUDED.status,
    allergens_confirmed = EXCLUDED.allergens_confirmed,
    created_by = EXCLUDED.created_by,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    price = EXCLUDED.price,
    retail_price = EXCLUDED.retail_price,
    published_at = EXCLUDED.published_at,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- 2. Map Dietary Restrictions and Allergens to Dishes
INSERT INTO dish_dietary_restrictions (dish_id, restriction_code)
VALUES
    -- Ensalada Mediterránea: Celiac, Vegetarian
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'CELIAC'),
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'VEGETARIAN'),

    -- Pad Thai con Maní: Peanuts, Soy
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'PEANUTS'),
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'SOY'),

    -- Hamburguesa Clásica: Wheat, Dairy
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'WHEAT'),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'DAIRY'),

    -- Bowl Vegano Proteico: Vegan, Vegetarian, Celiac, Soy
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'VEGAN'),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'VEGETARIAN'),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'CELIAC'),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'SOY'),

    -- Salmón Grillado: Celiac, Fish
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'CELIAC'),
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'FISH')
ON CONFLICT (dish_id, restriction_code) DO NOTHING;
