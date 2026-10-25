-- ====================================================================
-- EasyEat: Dev Profile Repeatable Seed - Dishes & Dietary Mapping
-- Establishment Tenant: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 (Admin synthetic tenant)
-- ====================================================================

-- 1. Insert Sample Dishes
INSERT INTO dishes (id, tenant_id, name, description, price, is_active, created_at, updated_at)
VALUES
    ('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Ensalada Mediterránea', 'Mix de hojas verdes, tomates secos, queso feta y aceitunas negras.', 4500.00, true, NOW(), NOW()),
    ('d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Pad Thai con Maní', 'Fideos de arroz salteados con verduras, salsa de soja y maní tostado picado.', 6200.00, true, NOW(), NOW()),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Hamburguesa Clásica con Queso', 'Medallón de carne vacuna, queso cheddar y pan brioche artesanal.', 7500.00, true, NOW(), NOW()),
    ('d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a04', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Bowl Vegano Proteico', 'Quinoa, tofu crocante, palta, espinaca baby y aderezo de tahini.', 5800.00, true, NOW(), NOW()),
    ('d5eebc99-9c0b-4ef8-bb6d-6bb9bd380a05', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Salmón Grillado con Espárragos', 'Filete de salmón rosado a la plancha acompañado de espárragos y limón.', 9500.00, true, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    price = EXCLUDED.price,
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
