# Fase 2 · Integração Mercado Livre

## Objetivo
Automatizar a entrada de pedidos provenientes do Mercado Livre (ML) no Hub, reduzindo digitação manual e erros. Eventos recebidos via webhook devem criar ou atualizar pedidos e volumes, posicionar o status correto e disparar notificações de pronto para retirada quando aplicável.

## Escopo Funcional
- **Cadastro do conector ML**
  - Tela `Integrações > Conectores` para configurar `client_id`, `client_secret`, `refresh_token`, loja/tenant e `url_webhook` (somente leitura).
  - Botões: Testar, Sincronizar agora e Reautorizar.
  - Validação e armazenamento seguro dos segredos.
- **Recepção de eventos (Webhook ML)**
  - Endpoint público protegido (assinatura/shared secret ou IP allowlist).
  - Eventos de pedido, etiqueta e status são recebidos e persistidos.
  - Cada evento é enviado para fila interna (JMS) ou tabela outbox para processamento assíncrono.
- **Processamento assíncrono**
  - Worker consome mensagens da fila/outbox, aplica idempotência (chave `canal=ML + external_id + tipo_evento + versão`).
  - Enriquecimento com dados de destinatário, itens e etiqueta quando disponível.
  - Criação/atualização de pedido, volume e evento; ajuste de status e timestamps (`ready_at`).
- **Mapeamento de dados**
  - Regras configuráveis para correlacionar SKU externo ↔ SKU interno e status externo ↔ status interno.
  - Eventos com SKU não mapeado geram pendência "mapeamento necessário".
- **Notificação de pronto (plugável)**
  - Serviço de notificação com conectores e-mail/SMS/WhatsApp (mock/driver local nesta fase).
  - Template: código do pedido, endereço do Hub e horário de retirada.
- **Tela de Eventos & Integrações**
  - Timeline paginada com eventos por canal: recebido, processado, reprocessado, erro.
  - Ações: Reprocessar, Ver payload, Enviar novamente notificação.
- **Reprocessamentos e falhas**
  - Botão "Reprocessar" enfileira novamente com `reprocess_id`, mantendo auditoria.
  - Backoff exponencial automático para erros transitórios.

## Requisitos Não Funcionais
- Idempotência rígida (constraint única em tabela de deduplicação).
- Resiliência com retry, backoff e circuit breaker para chamadas externas.
- Timeout máximo de 5 segundos por requisição externa.
- Segurança com validação de assinatura do webhook e TLS obrigatório.
- Observabilidade: logs estruturados (`traceId`, `eventId`, `tenant`), métricas (erro/latência do consumidor, backlog da fila), health/readiness.
- Conformidade LGPD: armazenar apenas dados mínimos do destinatário e mascarar PII em logs.

## APIs REST
- **Inbound (webhook)**
  - `POST /api/integracoes/ml/webhook`
    - Headers: `X-ML-Signature` (ou similar), `X-Event-Type`, `X-Tenant`.
    - Body: JSON com eventos (pedido criado/atualizado, etiqueta disponível etc.).
    - Resposta: `202 Accepted` quando o evento é enfileirado com sucesso.
- **Internas (processamento/apoio)**
  - `POST /internal/integracoes/ml/enfileirar`
  - `POST /internal/integracoes/ml/reprocessar/{eventId}`
  - `GET /api/integracoes/eventos?canal=ML&status&data`
  - `POST /api/notificacoes/pedido/{pedidoId}/pronto`

## Modelo de Dados
- **Novas tabelas**
  - `integracao_conector(id, canal, tenant_id, client_id, client_secret_enc, refresh_token_enc, status, last_sync_at, created_at, updated_at)`
  - `integracao_evento(id, canal, tenant_id, external_id, tipo, payload_json, received_at, processed_at, status, error_msg, reprocess_count, trace_id)`
  - `idempotencia(chave, created_at)` com chave composta `canal|external_id|tipo|versao`.
- **Tabelas alteradas**
  - `pedido` adicionar `canal`, `external_id` (índice único com canal e tenant), `ready_at`, `picked_up_at`.
  - `volume` adicionar `etiqueta_externa`, `peso`, `dimensoes`, `status`, `posicao_id`.
- **Índices sugeridos**
  - `pedido(tenant_id, canal, status, created_at)`
  - `integracao_evento(tenant_id, canal, received_at)`
  - `idempotencia(chave)` unique

## Regras de Negócio
- Criar pedido/volume quando `external_id + canal` não existir; atualizar dados quando existir.
- Status internos seguem fluxo `RECEBIDO → PRONTO → RETIRADO` (compatível com Fase 1).
- Etiqueta disponível atualiza volume e define `ready_at` quando pré-condições satisfeitas.
- Notificação enviada uma vez por pedido ao entrar em `PRONTO`, com possibilidade de reenvio manual.
- Eventos duplicados (mesma chave de idempotência) são descartados e anotados como "duplicado" em `integracao_evento`.
- Mapeamentos configuráveis: `mapeamento_sku(canal, sku_externo, sku_interno, tenant_id)` e `mapeamento_status(canal, status_externo, status_interno, terminal)`.
- Campos mínimos do pedido: `external_id`, `destinatario.nome`, `destinatario.doc`, `destinatario.telefone`, `itens[]`, `volume{peso, dimensoes}`, `etiqueta` (quando disponível).

## Estratégia Técnica
- **Fila/Outbox**: utilizar JMS no WildFly ou fallback em tabela outbox com job de consumo e lock otimista.
- **Idempotência**: inserir na tabela `idempotencia` antes do processamento; em `unique_violation`, pular processamento.
- **Backoff**: reintentos em 30s, 2min e 10min (configurável).
- **Segurança do webhook**: header secreto compartilhado e/ou assinatura HMAC do corpo.
- **Gestão de segredos**: criptografar `client_secret` e `refresh_token` com sal, nunca logar valores.
- **Observabilidade**: gerar ou propagar `traceId`, correlacionando webhook → evento → pedido.

## DDL Sugerida (Flyway V2)
```sql
create table integracao_conector (
  id bigserial primary key,
  canal varchar(32) not null,
  tenant_id bigint not null,
  client_id varchar(128) not null,
  client_secret_enc text not null,
  refresh_token_enc text,
  status varchar(16) not null default 'ATIVO',
  last_sync_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table integracao_evento (
  id bigserial primary key,
  canal varchar(32) not null,
  tenant_id bigint not null,
  external_id varchar(128) not null,
  tipo varchar(64) not null,
  payload_json jsonb not null,
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  status varchar(16) not null default 'RECEBIDO',
  error_msg text,
  reprocess_count int not null default 0,
  trace_id uuid not null default gen_random_uuid()
);

create table idempotencia (
  chave varchar(256) primary key,
  created_at timestamptz not null default now()
);

create unique index if not exists ux_pedido_external on pedido(tenant_id, canal, external_id);
create index if not exists ix_evento_tenant_canal on integracao_evento(tenant_id, canal, received_at);
```

## Contratos Internos
- **Mensagem enfileirada (exemplo)**
```json
{
  "canal": "ML",
  "tenantId": 123,
  "tipo": "ORDER_CREATED",
  "externalId": "ML-ORD-987654",
  "versao": 1,
  "payload": { "...": "conteúdo do webhook" },
  "traceId": "a8a4b8e0-...."
}
```
- **Chave de idempotência**
  - `ML|123|ORDER_CREATED|ML-ORD-987654|v1`

## Testes Recomendados
- **Unidade**: gerador de chave de idempotência, mapeamentos SKU/status, parser de payload.
- **Integração**: webhook → persistência em `integracao_evento`; worker cria/atualiza pedido sem duplicar registros.
- **Notificação**: envio único quando pedido passa para `PRONTO` e reenvio manual via UI.
- **Reprocessamento**: botão de reprocessar enfileira novamente e atualiza auditoria/contadores.

## Rollout
- Habilitar feature flag `integracao.ml.enabled` por tenant.
- Aplicar migração Flyway V2.
- Cadastrar conector em `integracao_conector` e executar fluxo de Reautorizar/Testar.
- Monitorar backlog da fila e taxa de erro nas primeiras 24-48h.

## Validações em Python
- `python -m pytest tests/fase_02/test_webhook_ml.py` para validar assinatura, idempotência e persistência do evento.
- `python -m pytest tests/fase_02/test_worker_ml.py` para conferir criação/atualização de pedidos e disparo de notificações.
- `python scripts/validadores/monitorar_fila.py --canal ML` para verificar métricas de backlog e reprocessamentos pendentes.
