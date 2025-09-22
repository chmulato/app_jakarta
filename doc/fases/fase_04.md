# Fase 4 · UX, Relatórios, Erros e Auditoria

## Objetivo
Elevar a eficiência do Hub com UX refinada, relatórios operacionais, centro de erros com reprocessamento, auditoria alinhada à LGPD e exportações assíncronas (CSV), além de observabilidade avançada e melhorias de acessibilidade.

## Escopo Funcional
- **Relatórios operacionais**
  - Painel de KPIs com: entradas do dia, prontos, retirados, lead time (p50/p90) e backlog por canal/faixa (0-24/24-48/48-72/72+ horas).
  - Relatórios detalhados com filtros e paginação server-side: ciclo do pedido (timestamps, operador, SLA), divergências (recebimento/inventário/etiqueta) e eficiência de balcão (tempo médio por operador/turno).
  - Drill-down: cada linha abre Detalhe do Pedido ou Evento.
- **Exportações assíncronas (CSV)**
  - Tela `Exportações` para agendar e baixar: pedidos, movimentações de estoque, eventos de integração, divergências.
  - Geração assíncrona via job + notificação quando concluir.
  - Armazenamento temporário (7 dias) com link protegido por token.
- **Centro de Erros & Reprocessamento**
  - Tela com cards agrupando erros por tipo (HTTP 4xx/5xx/timeout/validação), canal e endpoint.
  - Mostrar contagem, última ocorrência e exemplos de payload.
  - Ações: Reprocessar, Reenviar notificação, Abrir como divergência (quando aplicável).
  - Integração direta com `integracao_evento` e logs (`traceId`).
- **Auditoria & LGPD**
  - Tela de auditoria com filtros por usuário, entidade, ação e período.
  - Rastreamento de login, criação/edição de pedido/volume/posição, retirada, reprocessamento e mudanças de credenciais.
  - Logs com PII mascarada; exportação de auditoria exige justificativa.
- **UX & Acessibilidade**
  - Layout responsivo com navegação lateral fixa, breadcrumbs, estados de carregamento e validações inline.
  - Atalhos de teclado para operações frequentes (F2 scan/retirada, F4 conferir etc.).
  - Conformidade de acessibilidade: contraste AA, foco visível, labels/aria, suporte a leitor de tela.
  - Internacionalização básica (PT-BR base, chaves i18n na UI).

## Requisitos Não Funcionais
- Performance de relatórios usando views/materialized views e paginação server-side.
- Observabilidade com métricas de latência por endpoint, backlog de fila, taxa de erro por conector e tempo de geração de export.
- Tracing distribuído (OpenTelemetry) correlacionando webhook → processamento → UI.
- Segurança: RBAC nas telas de relatórios/export/auditoria; downloads com token de uso único e expiração.
- Confiabilidade: reprocessamento idempotente e auditado.

## Banco de Dados
- **Novas tabelas**
  - `relatorio_export(id, tenant_id, tipo, filtros_json, status, arquivo_path, created_at, finished_at, requested_by, motivo)`
  - `auditoria(id, tenant_id, entidade, entidade_id, acao, antes_json, depois_json, actor, created_at, trace_id)`
  - `erro_integracao(id, tenant_id, canal, tipo, endpoint, payload_json, http_status, error_msg, occurred_at, trace_id)` (opcional se não usar somente `integracao_evento`).
- **Views / Materialized views**
  - `vw_kpi_dia` (entradas, prontos, retirados por dia/tenant/canal).
  - `mv_lead_time_pedidos` (deltas `ready_at - created_at`, `picked_up_at - ready_at`).
  - `vw_backlog_por_faixa` (contagem de pedidos `PRONTO` por faixa de horas).
  - Agendar `REFRESH MATERIALIZED VIEW CONCURRENTLY mv_lead_time_pedidos` a cada 5-10 min.
- **Índices**
  - `pedido(tenant_id, status, created_at)`; `pedido(tenant_id, ready_at)`; `pedido(tenant_id, picked_up_at)`.
  - `auditoria(tenant_id, created_at, entidade)`.
  - `integracao_evento(tenant_id, status, received_at)`.
  - `relatorio_export(tenant_id, status, created_at)`.

## APIs REST
- **Relatórios**
  - `GET /api/relatorios/kpis?inicio&fim&canal`
  - `GET /api/relatorios/ciclo?pagina&tamanho&filtros`
  - `GET /api/relatorios/divergencias?filtros`
  - `GET /api/relatorios/eficiencia-balcao?inicio&fim`
- **Exportações**
  - `POST /api/exportacoes`
  - `GET /api/exportacoes/{id}` (status/download)
  - `POST /api/exportacoes/{id}/reprocessar`
- **Centro de Erros**
  - `GET /api/erros?canal&tipo&periodo`
  - `POST /api/erros/{id}/reprocessar`
- **Auditoria**
  - `GET /api/auditoria?entidade&acao&usuario&inicio&fim`
  - `POST /api/auditoria/exportar`

## Observabilidade
- Métricas (MicroProfile Metrics):
  - `app_kpi_pedidos_recebidos_total`, `app_kpi_prontos_total`, `app_kpi_retirados_total`.
  - `app_lead_time_ready_ms{canal}`, `app_lead_time_pickup_ms{canal}`.
  - `app_export_jobs_em_fila`, `app_export_job_duracao_ms`.
  - `app_integracao_erros_total{canal,tipo}`.
- Tracing (OpenTelemetry): propagar `traceId` da entrada do webhook até centro de erros e relatórios.
- Logs estruturados em JSON com `tenant`, `usuario`, `traceId`, `acao`, `entidade`, `resultado`.

## Critérios de Aceite
- Dashboard exibe KPIs e backlog por faixa com dados validados por amostras SQL.
- Relatórios listam e filtram com paginação server-side (`p95 < 800ms`).
- Exportações: usuário agenda, recebe notificação quando pronto e baixa CSV; link expira corretamente.
- Centro de erros permite reprocessar e apresenta histórico por `traceId`.
- Auditoria registra ações críticas, filtra por qualquer combinação e oculta PII em claro.
- Acessibilidade: navegação por teclado e contraste AA atendidos.

## Testes Recomendados
- **Unidade**: agregadores de métricas, calculadora de lead time, gerador de tokens de download, mascaramento de PII.
- **Integração**: geração/refresh de materialized views, job de export produz arquivo correto, reprocessamento altera status do evento.
- **Carga**: 100k pedidos e 500k eventos → relatórios respondem < 1s (`p95`) com views atualizadas.
- **Segurança**: RBAC das páginas, tokens de download inválidos/expirados bloqueados, logs sem PII.

## Rollout
- Aplicar migração Flyway V4.
- Configurar jobs (refresh de views e processador de exportações).
- Publicar métricas no Prometheus e montar dashboards (lead time, backlog, erros, exportações).
- Treinar equipe em exportações com justificativa, uso do centro de erros e leitura de auditoria.

## Validações em Python
- `python -m pytest tests/fase_04/test_relatorios.py` para garantir agregações, paginação e tempos de resposta.
- `python -m pytest tests/fase_04/test_exportacoes.py` cobrindo agendamento, geração e download com token.
- `python scripts/validadores/auditoria.py --modo compliance` para checar mascaramento de PII, trilha de auditoria e políticas de expiração de links.
