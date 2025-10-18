-- ====================================================================
-- V2__fase2_integracao_ml.sql
-- Fase 2: Integração Mercado Livre
-- ====================================================================

-- Nova tabela para conectores de integração
CREATE TABLE integracao_conector (
    id BIGSERIAL PRIMARY KEY,
    canal VARCHAR(32) NOT NULL,
    tenant_id BIGINT NOT NULL,
    client_id VARCHAR(128) NOT NULL,
    client_secret_enc TEXT NOT NULL,
    refresh_token_enc TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'ATIVO',
    last_sync_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT ck_conector_canal CHECK (canal IN ('ML', 'SHOPEE', 'B2W', 'AMAZON')),
    CONSTRAINT ck_conector_status CHECK (status IN ('ATIVO', 'INATIVO', 'ERRO')),
    CONSTRAINT ux_conector_canal_tenant UNIQUE (canal, tenant_id)
);

-- Nova tabela para eventos de integração
CREATE TABLE integracao_evento (
    id BIGSERIAL PRIMARY KEY,
    canal VARCHAR(32) NOT NULL,
    tenant_id BIGINT NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    tipo VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL DEFAULT 'RECEBIDO',
    error_msg TEXT,
    reprocess_count INT NOT NULL DEFAULT 0,
    trace_id UUID NOT NULL DEFAULT gen_random_uuid(),
    
    CONSTRAINT ck_evento_canal CHECK (canal IN ('ML', 'SHOPEE', 'B2W', 'AMAZON')),
    CONSTRAINT ck_evento_status CHECK (status IN ('RECEBIDO', 'PROCESSANDO', 'PROCESSADO', 'ERRO', 'IGNORADO')),
    CONSTRAINT ck_evento_tipo CHECK (tipo IN ('ORDER_CREATED', 'ORDER_UPDATED', 'LABEL_AVAILABLE', 'STATUS_CHANGED'))
);

-- Nova tabela para idempotência
CREATE TABLE idempotencia (
    chave VARCHAR(256) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tabela para mapeamento de SKUs
CREATE TABLE mapeamento_sku (
    id BIGSERIAL PRIMARY KEY,
    canal VARCHAR(32) NOT NULL,
    tenant_id BIGINT NOT NULL,
    sku_externo VARCHAR(128) NOT NULL,
    sku_interno VARCHAR(128) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT ck_mapeamento_sku_canal CHECK (canal IN ('ML', 'SHOPEE', 'B2W', 'AMAZON')),
    CONSTRAINT ux_mapeamento_sku UNIQUE (canal, tenant_id, sku_externo)
);

-- Tabela para mapeamento de status
CREATE TABLE mapeamento_status (
    id BIGSERIAL PRIMARY KEY,
    canal VARCHAR(32) NOT NULL,
    tenant_id BIGINT NOT NULL,
    status_externo VARCHAR(64) NOT NULL,
    status_interno VARCHAR(32) NOT NULL,
    terminal BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT ck_mapeamento_status_canal CHECK (canal IN ('ML', 'SHOPEE', 'B2W', 'AMAZON')),
    CONSTRAINT ck_mapeamento_status_interno CHECK (status_interno IN ('RECEBIDO', 'PRONTO', 'RETIRADO', 'CANCELADO')),
    CONSTRAINT ux_mapeamento_status UNIQUE (canal, tenant_id, status_externo)
);

-- Alterações na tabela pedido para suportar integrações
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS canal VARCHAR(32);
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS external_id VARCHAR(128);
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS ready_at TIMESTAMPTZ;
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS picked_up_at TIMESTAMPTZ;

-- Alterações na tabela volume para dados da integração
ALTER TABLE volume ADD COLUMN IF NOT EXISTS etiqueta_externa VARCHAR(256);
ALTER TABLE volume ADD COLUMN IF NOT EXISTS peso DECIMAL(8,3);
ALTER TABLE volume ADD COLUMN IF NOT EXISTS dimensoes JSONB; -- {altura, largura, profundidade}
ALTER TABLE volume ADD COLUMN IF NOT EXISTS posicao_id BIGINT;

-- Novos índices para performance
CREATE INDEX IF NOT EXISTS ix_pedido_tenant_canal_status ON pedido(tenant_id, canal, status, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pedido_external ON pedido(tenant_id, canal, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_evento_tenant_canal ON integracao_evento(tenant_id, canal, received_at);
CREATE INDEX IF NOT EXISTS ix_evento_status_received ON integracao_evento(status, received_at);
CREATE INDEX IF NOT EXISTS ix_evento_trace_id ON integracao_evento(trace_id);
CREATE INDEX IF NOT EXISTS ix_conector_tenant_status ON integracao_conector(tenant_id, status);

-- Comentários para documentação
COMMENT ON TABLE integracao_conector IS 'Configuração dos conectores de integração por canal (ML, Shopee, etc.)';
COMMENT ON TABLE integracao_evento IS 'Log de eventos recebidos via webhook das integrações';
COMMENT ON TABLE idempotencia IS 'Controle de idempotência para evitar processamento duplicado';
COMMENT ON TABLE mapeamento_sku IS 'Mapeamento entre SKUs externos e internos por canal';
COMMENT ON TABLE mapeamento_status IS 'Mapeamento entre status externos e internos por canal';

COMMENT ON COLUMN pedido.canal IS 'Canal de origem do pedido (ML, SHOPEE, etc.)';
COMMENT ON COLUMN pedido.external_id IS 'ID do pedido no sistema externo';
COMMENT ON COLUMN pedido.ready_at IS 'Timestamp quando o pedido ficou pronto para retirada';
COMMENT ON COLUMN pedido.picked_up_at IS 'Timestamp quando o pedido foi retirado';

COMMENT ON COLUMN volume.etiqueta_externa IS 'Código da etiqueta fornecida pelo canal externo';
COMMENT ON COLUMN volume.peso IS 'Peso do volume em kg';
COMMENT ON COLUMN volume.dimensoes IS 'Dimensões do volume em JSON {altura, largura, profundidade} em cm';
COMMENT ON COLUMN volume.posicao_id IS 'Referência para a posição onde o volume está alocado';