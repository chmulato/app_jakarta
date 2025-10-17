-- Bootstrap script executed on container start. Reuses the same SQL files used by Flyway migrations
-- to keep Docker init and application migrations perfectly aligned.
\set ON_ERROR_STOP on

\echo '>> Applying baseline schema (V1__fase1_schema.sql)'
\i '/docker-entrypoint-initdb.d/V1__fase1_schema.sql'

\echo '>> Applying seed data (V1_1__fase1_seed.sql)'
\i '/docker-entrypoint-initdb.d/V1_1__fase1_seed.sql'

-- Guarantee at least one administrator even if the seed is changed in the future
DO $$
DECLARE
    v_admin_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_admin_count FROM usuarios WHERE perfil = 'ADMIN';
    IF v_admin_count = 0 THEN
        INSERT INTO usuarios (nome, email, senha, perfil)
        VALUES ('Administrador', 'admin@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'ADMIN');
        RAISE NOTICE 'Usuario ADMIN default criado (admin@meuapp.com). Altere a senha apos o primeiro login.';
    ELSE
        RAISE NOTICE 'Usuarios ADMIN existentes: %', v_admin_count;
    END IF;
END $$;

-- Quick summary to help with troubleshooting during container bootstrap
DO $$
BEGIN
    RAISE NOTICE 'Base de dados Fase 1 inicializada com sucesso!';
    RAISE NOTICE 'Usuarios cadastrados: %', (SELECT COUNT(*) FROM usuarios);
    RAISE NOTICE 'Posicoes cadastradas: %', (SELECT COUNT(*) FROM posicao);
    RAISE NOTICE 'Pedidos cadastrados: %', (SELECT COUNT(*) FROM pedido);
END $$;
