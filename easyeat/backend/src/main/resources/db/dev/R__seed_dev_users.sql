-- Seed migration for dev profile: Synthetic Tenant, Staff, and Comensal accounts
-- Passwords:
--   - admin@easyeat.com: SecurePass123 (BCrypt cost 12: $2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2)
--   - All other accounts: Password123 (BCrypt cost 12: $2a$12$jX0y55Hlj7f32Cz8lrFyMO97TFGkjX/8gWFoa.TtWL422i8qaU9/C)

-- 1. Synthetic Admin (from Spec 001)
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
) ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, updated_at = NOW();

-- 2. Encargado (Counter Manager)
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
    'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a23',
    'maria.encargada@laesquina.com',
    '$2a$12$jX0y55Hlj7f32Cz8lrFyMO97TFGkjX/8gWFoa.TtWL422i8qaU9/C',
    'ENCARGADO',
    'ACTIVE',
    '8bb12c8a-2d4e-4f76-a0b2-9a0910f13341',
    0,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 3. Chef (Kitchen Staff)
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
    'b2eebc99-9c0b-4ef8-bb6d-6bb9bd380a24',
    'carlos.chef@comedorcentral.com',
    '$2a$12$jX0y55Hlj7f32Cz8lrFyMO97TFGkjX/8gWFoa.TtWL422i8qaU9/C',
    'CHEF',
    'ACTIVE',
    '9cc23d9b-3e5f-4a87-b1c3-0b1a21e24452',
    0,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 4. Administrador (Local Admin)
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
) ON CONFLICT (email) DO NOTHING;

-- 5. Comensal (Diner)
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
    'b4eebc99-9c0b-4ef8-bb6d-6bb9bd380a26',
    'juan.comensal@gmail.com',
    '$2a$12$jX0y55Hlj7f32Cz8lrFyMO97TFGkjX/8gWFoa.TtWL422i8qaU9/C',
    'COMENSAL',
    'ACTIVE',
    NULL,
    0,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;
