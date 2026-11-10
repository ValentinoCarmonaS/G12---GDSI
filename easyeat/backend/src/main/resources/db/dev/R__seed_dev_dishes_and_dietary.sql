-- ====================================================================
-- EasyEat: Dev Profile Repeatable Seed - Dishes & Dietary Mapping
-- Establishment Tenants:
--   - a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 (Café Central Palermo / Spec 005 & 007 tests)
--   - 1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563 (Café Parque active tenant)
-- ====================================================================

-- 0. Ensure Admin users exist for dishes created_by foreign keys
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
) ON CONFLICT (email) DO UPDATE SET tenant_id = EXCLUDED.tenant_id, updated_at = NOW();

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
    'b3eebc99-9c0b-4ef8-bb6d-6bb9bd380a25',
    'laura.admin@cafeparque.com',
    '$2a$12$jX0y55Hlj7f32Cz8lrFyMO97TFGkjX/8gWFoa.TtWL422i8qaU9/C',
    'ADMINISTRADOR',
    'ACTIVE',
    '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563',
    0,
    NOW(),
    NOW()
) ON CONFLICT (email) DO UPDATE SET tenant_id = EXCLUDED.tenant_id, updated_at = NOW();

-- 1. Insert Sample Dishes for Café Central Palermo (a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11)
INSERT INTO dishes (id, tenant_id, name, description, category, price, retail_price, published_at, status, allergens_confirmed, created_by, is_active, created_at, updated_at)
VALUES
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Ensalada Mediterránea', 'Mix de hojas verdes, tomates secos, queso feta y aceitunas negras.', 'Ensaladas', 4500.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW()),
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Pad Thai con Maní', 'Fideos de arroz salteados con verduras, salsa de soja y maní tostado picado.', 'Platos principales', 6200.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW()),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Hamburguesa Clásica con Queso', 'Medallón de carne vacuna, queso cheddar y pan brioche artesanal.', 'Platos principales', 7500.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW()),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Bowl Vegano Proteico', 'Quinoa, tofu crocante, palta, espinaca baby y aderezo de tahini.', 'Platos principales', 5800.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW()),
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Salmón Grillado con Espárragos', 'Filete de salmón rosado a la plancha acompañado de espárragos y limón.', 'Platos principales', 9500.00, NULL, NULL, 'PENDING_PRICE', true, 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
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

-- 2. Insert Sample Dishes for Café Parque (1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563)
INSERT INTO dishes (id, tenant_id, name, description, category, price, retail_price, published_at, status, allergens_confirmed, created_by, is_active, created_at, updated_at)
VALUES
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'Ensalada Parque Verde', 'Mix de hojas verdes, tomates secos, queso feta y aceitunas negras.', 'Ensaladas', 4500.00, 4500.00, NOW(), 'PUBLISHED', true, 'b3eebc99-9c0b-4ef8-bb6d-6bb9bd380a25', true, NOW(), NOW()),
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'Pad Thai Especial Parque', 'Fideos de arroz salteados con verduras, salsa de soja y maní tostado picado.', 'Platos principales', 6200.00, 6200.00, NOW(), 'PUBLISHED', true, 'b3eebc99-9c0b-4ef8-bb6d-6bb9bd380a25', true, NOW(), NOW()),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'Burger Porteña', 'Medallón de carne vacuna, queso cheddar y pan brioche artesanal.', 'Platos principales', 7500.00, 7500.00, NOW(), 'PUBLISHED', true, 'b3eebc99-9c0b-4ef8-bb6d-6bb9bd380a25', true, NOW(), NOW()),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'Bowl Veggie Green', 'Quinoa, tofu crocante, palta, espinaca baby y aderezo de tahini.', 'Platos principales', 5800.00, 5800.00, NOW(), 'PUBLISHED', true, 'b3eebc99-9c0b-4ef8-bb6d-6bb9bd380a25', true, NOW(), NOW()),
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', '1aa45e0c-4f6a-4b98-c2d4-1c2b32f35563', 'Salmón del Parque', 'Filete de salmón rosado a la plancha acompañado de espárragos y limón.', 'Platos principales', 9500.00, 9500.00, NOW(), 'PUBLISHED', true, 'b3eebc99-9c0b-4ef8-bb6d-6bb9bd380a25', true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
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

-- 3. Map Dietary Restrictions and Allergens to Dishes
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
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'FISH'),

    -- Café Parque mappings
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'CELIAC'),
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'VEGETARIAN'),
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'PEANUTS'),
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'SOY'),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'WHEAT'),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'DAIRY'),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'VEGAN'),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'VEGETARIAN'),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'CELIAC'),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'SOY'),
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'CELIAC'),
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'FISH')
ON CONFLICT (dish_id, restriction_code) DO NOTHING;
