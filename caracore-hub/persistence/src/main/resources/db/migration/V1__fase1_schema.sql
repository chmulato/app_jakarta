-- Flyway migration for Phase 1 baseline schema

CREATE TABLE IF NOT EXISTS usuarios (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    senha VARCHAR(255) NOT NULL,
    ativo BOOLEAN DEFAULT true,
    perfil VARCHAR(20) DEFAULT 'OPERADOR' CHECK (perfil IN ('ADMIN', 'SUPERVISOR', 'OPERADOR')),
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS posicao (
    id SERIAL PRIMARY KEY,
    rua VARCHAR(16) NOT NULL,
    modulo VARCHAR(16) NOT NULL,
    nivel VARCHAR(16) NOT NULL,
    caixa VARCHAR(16) NOT NULL,
    ocupada BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT ux_posicao_codigo UNIQUE (rua, modulo, nivel, caixa)
);

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

CREATE TABLE IF NOT EXISTS volume (
    id SERIAL PRIMARY KEY,
    pedido_id INTEGER NOT NULL REFERENCES pedido(id) ON DELETE CASCADE,
    etiqueta VARCHAR(64) NOT NULL UNIQUE,
    peso NUMERIC(10,2),
    dimensoes VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEBIDO' CHECK (status IN ('RECEBIDO', 'ALOCADO', 'PRONTO', 'RETIRADO')),
    posicao_id INTEGER REFERENCES posicao(id)
);

CREATE TABLE IF NOT EXISTS evento (
    id SERIAL PRIMARY KEY,
    pedido_id INTEGER NOT NULL REFERENCES pedido(id) ON DELETE CASCADE,
    tipo VARCHAR(32) NOT NULL CHECK (tipo IN ('CRIACAO', 'ATUALIZACAO', 'ALOCACAO', 'PRONTO', 'RETIRADA')),
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor VARCHAR(80) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usuarios_email ON usuarios(email);
CREATE INDEX IF NOT EXISTS idx_usuarios_ativo ON usuarios(ativo);
CREATE INDEX IF NOT EXISTS idx_posicao_ocupada ON posicao(ocupada);
CREATE INDEX IF NOT EXISTS idx_pedido_status ON pedido(status);
CREATE INDEX IF NOT EXISTS idx_pedido_created_at ON pedido(created_at);
CREATE INDEX IF NOT EXISTS idx_pedido_tenant ON pedido(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pedido_ready_at ON pedido(ready_at);
CREATE INDEX IF NOT EXISTS idx_pedido_picked_up_at ON pedido(picked_up_at);
CREATE INDEX IF NOT EXISTS idx_volume_status ON volume(status);
CREATE INDEX IF NOT EXISTS idx_volume_posicao ON volume(posicao_id);
CREATE INDEX IF NOT EXISTS idx_evento_pedido ON evento(pedido_id, created_at);
CREATE INDEX IF NOT EXISTS idx_evento_tipo ON evento(tipo);

COMMENT ON TABLE posicao IS 'Mapa fisico de posicoes do hub (rua/modulo/nivel/caixa)';
COMMENT ON TABLE pedido IS 'Pedidos recebidos no hub local';
COMMENT ON TABLE volume IS 'Volumes associados ao pedido';
COMMENT ON TABLE evento IS 'Eventos que descrevem o fluxo do pedido';
