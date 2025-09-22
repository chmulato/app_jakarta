# Fase 3 · Shopee e Temu + Estoque

## Objetivo
Adicionar conectores Shopee e Temu, replicando o pipeline de integração estabelecido com Mercado Livre, e evoluir o módulo de estoque físico com mapa de posições, inventário cíclico e conferência de retirada no balcão com scanner.

## Escopo Funcional
- **Conectores Shopee e Temu**
  - Tela de conectores passa a listar ML, Shopee e Temu.
  - Cadastro de credenciais, status, última sincronização e botões Testar/Reautorizar por conector.
  - Webhooks dedicados: `POST /api/integracoes/shopee/webhook` e `POST /api/integracoes/temu/webhook` utilizando o mesmo pipeline de fila/outbox e idempotência.
  - Mapeamentos independentes por canal para SKU e status; eventos suportados: pedido criado, etiqueta disponível, atualização de status.
- **Mapa de posições (Estoque)**
  - Tela `/estoque/mapa.jsp` com visualização por rua/módulo/nível/caixa.
  - Indicadores de ocupado/livre/reservado e busca por SKU, volume ou pedido.
  - Ação rápida "Mover volume" atualiza `posicao_id`.
- **Inventário cíclico**
  - Tela `/estoque/inventario.jsp` para criar tarefas de contagem (por SKU, posição ou faixa).
  - Operador registra contagem real; sistema reconcilia com saldo esperado e marca status `OK` ou `Divergência`.
  - Divergências geram registro em `inventario_resultado` com motivo/log.
- **Conferência de retirada (scanner)**
  - Tela `/retirada/scan.jsp` otimizada para leitor de código de barras.
  - Operador escaneia código do volume; sistema valida se pertence ao pedido e está `PRONTO`.
  - Feedback instantâneo (verde/vermelho). Confirmação marca `picked_up_at` e gera comprovante.
- **SLA de estada**
  - Regra de alerta quando `now - ready_at > X` dias marcando pedido como "Atrasado".
  - Dashboard exibe backlog de pedidos não retirados com buckets 24h/48h/72h.

## Requisitos Não Funcionais
- Consistência: todos os canais compartilham pipeline e regras de idempotência.
- Escalabilidade com filas segregadas por canal (`fila_ml`, `fila_shopee`, `fila_temu`).
- Resiliência com reprocessamentos, retry/backoff equivalentes à Fase 2.
- UI responsiva com identidade Bootstrap unificada.
- Auditoria obrigatória para inventário e movimentações (usuário, data, motivo).

## Banco de Dados
- **Novas tabelas**
  - `inventario_tarefa(id, tenant_id, criado_por, criado_em, tipo, status, filtro_sku, filtro_posicao)`
  - `inventario_resultado(id, tarefa_id, posicao_id, sku_id, qtd_esperada, qtd_contada, divergencia, contado_por, contado_em)`
- **Alterações**
  - `pedido` ampliar enum `canal` para incluir `SHOPEE` e `TEMU`.
  - `integracao_conector` suportar múltiplos canais.
  - `integracao_evento` permanece genérico; apenas expandir enum de canal.
  - `posicao` incluir flag `reservado` e índice por ocupação.

## APIs REST
- `POST /api/integracoes/shopee/webhook`
- `POST /api/integracoes/temu/webhook`
- `GET /api/estoque/mapa?filtros`
- `POST /api/estoque/mover/{volumeId}`
- `POST /api/estoque/inventario/tarefas`
- `POST /api/estoque/inventario/{tarefaId}/contagem`
- `POST /api/pedidos/{id}/retirada/scan`
- `GET /api/pedidos/backlog?dias=2`

## Interfaces JSP + Bootstrap
- `/integracoes/conectores.jsp` atualizado para suporte a múltiplos canais.
- `/estoque/mapa.jsp` exibindo grid responsivo com ocupação.
- `/estoque/inventario.jsp` para criar/gerenciar tarefas e registrar contagens.
- `/retirada/scan.jsp` com foco automático e atalhos para scanner.
- Dashboard com widget "Backlog por tempo de espera" (24h/48h/72h).

## Critérios de Aceite
- Conectores Shopee/Temu cadastrados e ativos recebem eventos via webhook e criam pedidos automaticamente.
- Eventos duplicados não geram registros extras (idempotência validada).
- Operador visualiza mapa de estoque e localiza qualquer volume.
- Inventário cíclico gera tarefa, coleta contagem e registra divergência.
- Retirada com scanner valida código em < 1s e bloqueia retirada errada.
- Dashboard mostra pedidos pendentes de retirada por bucket de tempo.

## Estratégia Técnica
- Reaproveitar pipeline da Fase 2, adicionando handlers específicos por canal.
- Serviços `ShopeeService` e `TemuService` para autenticação (OAuth/token) e parsing dedicado.
- UI compartilhar componentes JSP reutilizáveis diferenciados por canal.
- Mapa de estoque renderizado a partir de `posicao` + `volume`, com cache leve em memória.
- Inventário gera registros esperados e compara com contagem do operador; divergências disparam alerta/auditoria.

## Testes Recomendados
- **Unidade**: parsing de payload Shopee/Temu, geração de chave de idempotência, cálculo de divergências.
- **Integração**: webhooks simulados criam pedidos corretos; movimentação de volume atualiza mapa.
- **UI/Funcional**: mapa exibindo posições ocupadas/livres, fluxo completo de inventário e retirada com scanner.
- **Performance**: resposta de retirada < 1s mesmo com 10k volumes.

## Rollout
- Aplicar migração Flyway V3.
- Ativar conectores Shopee/Temu em `integracao_conector`.
- Treinar operadores nos novos fluxos de inventário e scanner.
- Monitorar backlog de retirada e divergências nos primeiros dias.

## Validações em Python
- `python -m pytest tests/fase_03/test_webhooks_multicanal.py` para garantir idempotência e roteamento por canal.
- `python -m pytest tests/fase_03/test_inventario.py` validando geração de tarefas, comparação de contagens e registro de divergências.
- `python scripts/validadores/testar_scanner.py --tenant demo` para simular leituras rápidas e verificar bloqueio de retirada incorreta.
