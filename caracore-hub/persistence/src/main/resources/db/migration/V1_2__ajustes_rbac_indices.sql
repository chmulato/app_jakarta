-- Ajustes de RBAC e índices após revisão Checklist Fase 1

ALTER TABLE usuarios
    ALTER COLUMN perfil SET DEFAULT 'OPERADOR';

ALTER TABLE usuarios
    DROP CONSTRAINT IF EXISTS usuarios_perfil_check;

ALTER TABLE usuarios
    ADD CONSTRAINT usuarios_perfil_check CHECK (perfil IN ('ADMIN', 'SUPERVISOR', 'OPERADOR'));

UPDATE usuarios
SET perfil = 'OPERADOR'
WHERE perfil = 'USUARIO';

CREATE INDEX IF NOT EXISTS idx_pedido_tenant ON pedido(tenant_id);
