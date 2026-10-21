-- Extend users table for staff membership and first login password change
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(255) NOT NULL DEFAULT 'Unnamed User',
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Update role constraint to support standard English and Spanish role constants
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role 
    CHECK (role IN ('ADMIN', 'CHEF', 'COUNTER_CLERK', 'DINER', 'ADMINISTRADOR', 'ENCARGADO', 'COMENSAL'));

-- Update status constraint to support pending first login status
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_status;
ALTER TABLE users ADD CONSTRAINT chk_users_status 
    CHECK (status IN ('PENDING_FIRST_LOGIN', 'ACTIVE', 'SUSPENDED', 'UNVERIFIED'));

CREATE INDEX IF NOT EXISTS idx_users_tenant_role ON users(tenant_id, role);
