-- Seed migration for dev and test profiles: Synthetic Tenant and Administrator account
-- Synthetic Tenant UUID: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
-- Default Admin credentials: admin@easyeat.com / SecurePass123 (BCrypt cost 12)

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
    '$2a$12$7k8mJ0x0yK4eB4L7N5wS3uQ9pX2aC5vB8nM1jL4oP7qR0tU3wY6eK',
    'ADMINISTRADOR',
    'ACTIVE',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    0,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;
