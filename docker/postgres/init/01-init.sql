-- Script de inicializacao do banco para a Fase 1 (MVP operacao local)
-- Executado automaticamente na primeira inicializacao do container

-- Tabela de usuarios (mantem estrutura existente)
CREATE TABLE IF NOT EXISTS usuarios (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    senha VARCHAR(255) NOT NULL,
    ativo BOOLEAN DEFAULT true,
    perfil VARCHAR(20) DEFAULT 'USUARIO' CHECK (perfil IN ('ADMIN', 'USUARIO')),
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_usuarios_email ON usuarios(email);
CREATE INDEX IF NOT EXISTS idx_usuarios_ativo ON usuarios(ativo);

-- Tabela de posicoes fisicas no estoque
CREATE TABLE IF NOT EXISTS posicao (
    id SERIAL PRIMARY KEY,
    rua VARCHAR(16) NOT NULL,
    modulo VARCHAR(16) NOT NULL,
    nivel VARCHAR(16) NOT NULL,
    caixa VARCHAR(16) NOT NULL,
    ocupada BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT ux_posicao_codigo UNIQUE (rua, modulo, nivel, caixa)
);

CREATE INDEX IF NOT EXISTS idx_posicao_ocupada ON posicao(ocupada);

-- Tabela de pedidos recebidos no hub
CREATE TABLE IF NOT EXISTS pedido (
    id SERIAL PRIMARY KEY,
    codigo VARCHAR(64) NOT NULL UNIQUE,
    canal VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    external_id VARCHAR(128),
    destinatario_nome VARCHAR(120) NOT NULL,
    destinatario_documento VARCHAR(32) NOT NULL,
    destinatario_telefone VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEBIDO' CHECK (status IN ('RECEBIDO', 'PRONTO', 'RETIRADO')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ready_at TIMESTAMP,
    picked_up_at TIMESTAMP,
    tenant_id BIGINT,
    CONSTRAINT ck_pedido_canal CHECK (canal IN ('MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_pedido_status ON pedido(status);
CREATE INDEX IF NOT EXISTS idx_pedido_created_at ON pedido(created_at);
CREATE INDEX IF NOT EXISTS idx_pedido_ready_at ON pedido(ready_at);
CREATE INDEX IF NOT EXISTS idx_pedido_picked_up_at ON pedido(picked_up_at);

-- Tabela de volumes associados a cada pedido
CREATE TABLE IF NOT EXISTS volume (
    id SERIAL PRIMARY KEY,
    pedido_id INTEGER NOT NULL REFERENCES pedido(id) ON DELETE CASCADE,
    etiqueta VARCHAR(64) NOT NULL UNIQUE,
    peso NUMERIC(10,2),
    dimensoes VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEBIDO' CHECK (status IN ('RECEBIDO', 'ALOCADO', 'PRONTO', 'RETIRADO')),
    posicao_id INTEGER REFERENCES posicao(id)
);

CREATE INDEX IF NOT EXISTS idx_volume_status ON volume(status);
CREATE INDEX IF NOT EXISTS idx_volume_posicao ON volume(posicao_id);

-- Tabela de eventos do ciclo do pedido
CREATE TABLE IF NOT EXISTS evento (
    id SERIAL PRIMARY KEY,
    pedido_id INTEGER NOT NULL REFERENCES pedido(id) ON DELETE CASCADE,
    tipo VARCHAR(32) NOT NULL CHECK (tipo IN ('CRIACAO', 'ATUALIZACAO', 'ALOCACAO', 'PRONTO', 'RETIRADA')),
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor VARCHAR(80) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_evento_pedido ON evento(pedido_id, created_at);
CREATE INDEX IF NOT EXISTS idx_evento_tipo ON evento(tipo);

-- Dados basicos para operacao local
INSERT INTO usuarios (nome, email, senha, perfil)
VALUES
    ('Admin Sistema', 'admin@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'ADMIN'),
    ('Supervisor Balcao', 'supervisor@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'USUARIO'),
    ('Operador 01', 'operador01@meuapp.com', '$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6', 'USUARIO')
ON CONFLICT (email) DO NOTHING;

-- Posicoes padrao (mini hub local)
INSERT INTO posicao (rua, modulo, nivel, caixa, ocupada) VALUES
    ('A', '01', '01', '01', false),
    ('A', '01', '01', '02', false),
    ('A', '01', '02', '01', false),
    ('A', '02', '01', '01', false),
    ('B', '01', '01', '01', false),
    ('B', '01', '02', '01', false)
ON CONFLICT DO NOTHING;

-- Pedido de exemplo para validacao manual da UI
INSERT INTO pedido (codigo, canal, destinatario_nome, destinatario_documento, destinatario_telefone, status)
VALUES ('PED-LOCAL-001', 'MANUAL', 'Cliente Exemplo', '11122233344', '11988887777', 'RECEBIDO')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO volume (pedido_id, etiqueta, peso, dimensoes, status)
SELECT p.id, 'PED-LOCAL-001-VOL-01', 2.50, '30x20x15', 'RECEBIDO'
FROM pedido p
WHERE p.codigo = 'PED-LOCAL-001'
ON CONFLICT (etiqueta) DO NOTHING;

INSERT INTO evento (pedido_id, tipo, payload, actor)
SELECT p.id, 'CRIACAO', 'Pedido criado via script de bootstrap', 'bootstrap'
FROM pedido p
WHERE p.codigo = 'PED-LOCAL-001'
ON CONFLICT DO NOTHING;

-- Comentarios para documentacao do schema
COMMENT ON TABLE posicao IS 'Mapa fisico de posicoes do hub (rua/modulo/nivel/caixa)';
COMMENT ON TABLE pedido IS 'Pedidos recebidos no hub local';
COMMENT ON TABLE volume IS 'Volumes associados ao pedido';
COMMENT ON TABLE evento IS 'Eventos que descrevem o fluxo do pedido';

-- Garantir ao menos um admin
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

-- Estatisticas rapidas
DO $$
BEGIN
    RAISE NOTICE 'Base de dados Fase 1 inicializada com sucesso!';
    RAISE NOTICE 'Usuarios cadastrados: %', (SELECT COUNT(*) FROM usuarios);
    RAISE NOTICE 'Posicoes cadastradas: %', (SELECT COUNT(*) FROM posicao);
    RAISE NOTICE 'Pedidos cadastrados: %', (SELECT COUNT(*) FROM pedido);
END $$;

-- Recomendada politica de senha: alterar hash padrao apos bootstrap.
