# Checklist de Desenvolvimento do Hub (Fases 1–4)

> Atualização 17/out/2025 · Fase 1 concluída e validada (ver [doc/fases/fase_01.md](../fases/fase_01.md)). Fase 2 ativa; progresso será acompanhado aqui e em [doc/fases/fase_02.md](../fases/fase_02.md).

Este documento organiza todas as entregas em formato de checklist por fase.  
Cada item pode ser marcado conforme validado com **testes em Python (pytest/Testcontainers)**.

---

## ✅ Fase 1 — MVP Operação Local (sem integrações)

> Status em 17/out/2025: ✅ Concluída (execuções da opção 12 verdes + documentação atualizada). Detalhes no relatório em [doc/RESULTADOS-TESTES.md](../RESULTADOS-TESTES.md).

### Fundamentos

- [x] Projeto multi-módulos (api, web, core, persistence)
- [x] Flyway configurado e primeira migração aplicada
- [x] Logging JSON + correlação (trace_id) habilitado
- [x] Health/readiness endpoint ativo
- [x] RBAC: Admin, Supervisor, Operador

### Banco de dados

- [x] Tabela `usuario`
- [x] Tabela `pedido`
- [x] Tabela `volume`
- [x] Tabela `posicao`
- [x] Tabela `evento`
- [x] Índices principais (`tenant_id,status,created_at` etc.)

### Telas JSP + Bootstrap

- [x] `/login.jsp` (login, feedback de erro)
- [x] `/dashboard.jsp` (contadores do dia)
- [x] `/recebimento/entrada.jsp` (criar pedido+volume)
- [x] `/triagem/alocar.jsp` (sugerir/confirmar posição)
- [x] `/pedidos/lista.jsp` + filtros
- [x] `/pedidos/detalhe.jsp`
- [x] `/retirada/retirar.jsp` (pronto → retirado)
- [x] Geração de **PDF etiqueta** e **PDF comprovante**

### APIs mínimas

- [x] `POST /api/pedidos`
- [x] `GET /api/pedidos?status&canal&data`
- [x] `POST /api/pedidos/{id}/ready`
- [x] `POST /api/pedidos/{id}/pickup`

### Critérios de aceite (Fase 1)

- [x] Fluxo completo manual: **RECEBIDO → PRONTO → RETIRADO**
- [x] Pesquisa por código/telefone operando
- [x] Comprovante PDF anexado ao pedido

### Suíte de testes Python (Fase 1)

- [x] `tests/test_auth.py::test_login_success_e_rbac`
- [x] `tests/test_pedidos.py::test_criar_pedido_e_volume`
- [x] `tests/test_pedidos.py::test_marcar_pronto_e_retirar`
- [x] `tests/test_pdf.py::test_gera_etiqueta_e_comprovante`
- [x] `tests/test_db.py::test_indices_e_constraints_basicas`
- [x] `tests/test_ui.py::test_listagem_filtra_por_status`

---

## ✅ Fase 2 — Integração Mercado Livre

> Status em 17/out/2025: 🟡 Em andamento. Planejamento detalhado e decisões em [doc/fases/fase_02.md](../fases/fase_02.md).

### Conector ML

- [ ] Tela **Conectores** com form seguro (client_id/secret/refresh)
- [ ] Botões: **Testar**, **Reautorizar**, **Sincronizar agora**
- [ ] Segredos cifrados em coluna

### Webhook + Pipeline

- [ ] `POST /api/integracoes/ml/webhook` (valida assinatura/segredo)
- [ ] Enfileiramento (JMS) **ou** outbox-table
- [ ] Deduplicação: tabela `idempotencia` (PK `chave`)
- [ ] Persistência `integracao_evento` (payload, status, trace_id)

### Processamento assíncrono

- [ ] Worker consome e aplica idempotência
- [ ] Criação/atualização de `pedido/volume`
- [ ] Mapeamento SKU externo → interno
- [ ] Mapeamento status externo → interno
- [ ] Ao receber **etiqueta**: marca **PRONTO** + `ready_at`

### Notificação “pronto para retirada”

- [ ] Serviço plugável (email/SMS/WhatsApp mock)
- [ ] Envia **1x** por pedido ao entrar em PRONTO
- [ ] Reenvio manual pela UI

### UI Integrações & Eventos

- [ ] `/integracoes/eventos.jsp` (timeline, payload modal)
- [ ] Ações: **Reprocessar**, **Reenviar notificação**

### Observabilidade & Segurança

- [ ] Logs com `trace_id`, `event_id`, `tenant`
- [ ] Métricas: recebidos/processados/falhas/backlog/latência
- [ ] TLS + header secreto/HMAC no webhook

### Critérios de aceite (Fase 2)

- [ ] Webhook responde **202** e enfileira em ≤ 200ms
- [ ] Eventos duplicados **não** geram duplicata
- [ ] Etiqueta recebida ⇒ **PRONTO** + notificação registrada
- [ ] Reprocessamento via UI reexecuta com sucesso

### Suíte de testes Python (Fase 2)

- [ ] `tests/test_webhook_ml.py::test_202_enfileira_evento`
- [ ] `tests/test_webhook_ml.py::test_assinatura_invalida_rejeitada`
- [ ] `tests/test_worker_ml.py::test_idempotencia_descarta_duplicado`
- [ ] `tests/test_worker_ml.py::test_cria_ou_atualiza_pedido_com_mapeamento`
- [ ] `tests/test_notificacao.py::test_envio_unico_ao_mudar_para_pronto`
- [ ] `tests/test_reprocessamento.py::test_reprocessar_evento_via_ui`
- [ ] `tests/test_metrics.py::test_exposicao_metricas_basicas`

---

## ✅ Fase 3 — Shopee & Temu + Estoque/Prateleira

### Novos conectores

- [ ] Shopee: credenciais, webhook, handler
- [ ] Temu: credenciais, webhook, handler
- [ ] Filas separadas por canal
- [ ] Mapeamentos próprios (SKU/status)

### Estoque — Mapa de posições

- [ ] `/estoque/mapa.jsp` (grid ocupação, busca SKU/volume/pedido)
- [ ] Ação “Mover volume” (atualiza `posicao_id` + evento)

### Inventário cíclico

- [ ] `inventario_tarefa` e `inventario_resultado`
- [ ] Criar tarefas (por SKU/posição/faixa)
- [ ] Registrar contagens, calcular divergência
- [ ] Registrar auditoria de inventário

### Retirada com scanner (balcão)

- [ ] `/retirada/scan.jsp` (input focado)
- [ ] Valida se volume pertence ao pedido e está **PRONTO**
- [ ] Confirma, marca `picked_up_at` e gera comprovante

### SLA de estada

- [ ] Regras por faixa (0–24/24–48/48–72/72+ h)
- [ ] Widget no dashboard com backlog por faixa

### Critérios de aceite (Fase 3)

- [ ] Shopee/Temu operando com idempotência
- [ ] Mapa localiza volumes rapidamente
- [ ] Inventário gera divergências quando há diferenças
- [ ] Scanner responde em < 1s e bloqueia retirada errada

### Suíte de testes Python (Fase 3)

- [ ] `tests/test_webhook_shopee.py::test_fluxo_criacao_pedido`
- [ ] `tests/test_webhook_temu.py::test_fluxo_etiqueta_pronto`
- [ ] `tests/test_idempotencia.py::test_chaves_por_canal`
- [ ] `tests/test_estoque_mapa.py::test_busca_por_sku_volume`
- [ ] `tests/test_inventario.py::test_divergencia_calculada`
- [ ] `tests/test_retirada_scan.py::test_validacao_e_confirmacao_em_menos_de_um_segundo`

---

## ✅ Fase 4 — UX, Relatórios, Erros & Auditoria

### Relatórios operacionais

- [ ] KPIs no dashboard (Entradas, Prontos, Retirados)
- [ ] Lead time p50/p90 (Recebimento→Pronto, Pronto→Retirada)
- [ ] Backlog por canal e por faixa de espera
- [ ] Drill-down para pedido/evento

### Exportações assíncronas

- [ ] Tela de exportações (agendar/baixar)
- [ ] Job assíncrono gera CSV
- [ ] Link protegido com expiração (7 dias)
- [ ] Registro `relatorio_export` com motivo

### Centro de erros & reprocessamento

- [ ] Agrupar por tipo/canal/endpoint
- [ ] Ver payload + **Reprocessar** / **Reenviar notificação**
- [ ] Correlação via `trace_id`

### Auditoria & LGPD

- [ ] Tabela `auditoria` com diffs (antes/depois) mascarados
- [ ] Filtros por entidade/ação/usuário/período
- [ ] Export de auditoria exige “motivo”

### UX & Acessibilidade

- [ ] Nave lateral fixa, breadcrumbs, toasts
- [ ] Atalhos (F2 scan, F4 conferir)
- [ ] Contraste AA, foco visível, labels/aria

### Observabilidade

- [ ] Métricas: latências, backlog, falhas, tempo de export
- [ ] Tracing distribuído ponta a ponta (OpenTelemetry)

### Critérios de aceite (Fase 4)

- [ ] Relatórios paginados em p95 < 800ms
- [ ] Export → notificação → download com expiração
- [ ] Reprocessar via Centro de Erros funciona e audita
- [ ] Auditoria consultável; PII mascarada

### Suíte de testes Python (Fase 4)

- [ ] `tests/test_relatorios.py::test_kpis_e_lead_time_views`
- [ ] `tests/test_exportacoes.py::test_agendar_gerar_e_baixar_csv`
- [ ] `tests/test_erros.py::test_agrupamento_e_reprocessamento`
- [ ] `tests/test_auditoria.py::test_registro_e_mascara_pii`
- [ ] `tests/test_security.py::test_token_download_expira`

---

## Extras para automação de testes

- [ ] `pytest.ini` com markers (`e2e`, `integration`, `slow`)
- [ ] Testcontainers para Postgres (e broker JMS se usado)
- [ ] Fixtures: usuário admin, operador, dados seed
- [ ] Gerador de payloads por canal (ML/Shopee/Temu)
- [ ] Assertivas de idempotência (linhas não duplicadas)
- [ ] Testes de métricas/health endpoints
- [ ] Script de carga sintética (10k pedidos) p/ medir p95

---
