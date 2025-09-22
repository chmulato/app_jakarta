# Fase 1 · MVP Operação Local

## Objetivo
Construir a primeira versão funcional do Hub para operação em cidades pequenas, permitindo que operadores cadastrem manualmente pedidos recebidos, armazenem volumes e efetuem a entrega ou retirada no balcão.

## Escopo Funcional
- **Login e segurança**
  - Tela de login com autenticação baseada em usuários cadastrados (usuários criados pelo administrador).
  - Perfis disponíveis: Operador, Supervisor e Admin.
- **Dashboard inicial**
  - Visão com contadores do dia: pedidos recebidos, prontos para retirada e retirados.
- **Recebimento de mercadorias**
  - Tela para digitar ou escanear código de pedido.
  - Campos obrigatórios: nome do destinatário, documento, telefone e canal (manual nesta fase).
  - Cadastro de volumes com peso, dimensões e etiqueta.
  - Status inicial configurado como `RECEBIDO`.
  - Geração de etiqueta simples em PDF.
- **Alocação de posição**
  - Sugestão automática de posição disponível no estoque.
  - Tela para confirmar ou ajustar posição do volume.
- **Consulta de pedidos**
  - Tela com filtros por status, período, destinatário e canal.
  - Tabela com ações rápidas: detalhar, imprimir etiqueta e mover.
- **Retirada no balcão**
  - Localização de pedido por código ou telefone.
  - Validação do documento do cliente na entrega.
  - Atualização do status para `RETIRADO`.
  - Geração de comprovante em PDF contendo pedido, data/hora e operador.

## Requisitos Não Funcionais
- Segurança básica com senhas armazenadas via hash e sessão expirada por inatividade.
- Disponibilidade local dentro do horário comercial, com logs em arquivo para auditoria.
- Impressão de PDFs compatível com impressoras térmicas comuns.

## APIs REST (JAX-RS)
- `POST /api/pedidos` cria pedidos e respectivos volumes.
- `GET /api/pedidos?status&canal&data` consulta pedidos com filtros básicos.
- `POST /api/pedidos/{id}/ready` altera status para pronto.
- `POST /api/pedidos/{id}/pickup` confirma retirada do pedido.

## Banco de Dados (PostgreSQL)
- `usuario(id, nome, email, senha_hash, perfil, ativo)`
- `pedido(id, canal, external_id, destinatario, documento, telefone, status, created_at, ready_at, picked_up_at, tenant_id)`
- `volume(id, pedido_id, etiqueta, peso, dimensoes, posicao_id, status)`
- `posicao(id, rua, modulo, nivel, caixa, ocupacao)`
- `evento(id, pedido_id, tipo, payload, created_at, actor)`

## Interfaces JSP + Bootstrap
- `/login.jsp`
- `/dashboard.jsp`
- `/recebimento/entrada.jsp`
- `/triagem/alocar.jsp`
- `/pedidos/lista.jsp`
- `/pedidos/detalhe.jsp`
- `/retirada/retirar.jsp`

## Critérios de Aceite
- Usuário consegue autenticar e acessar o dashboard de forma segura.
- É possível registrar pedido manualmente com ao menos um volume associado.
- Pedido recém-criado aparece na lista com status `RECEBIDO`.
- Operador altera status de `RECEBIDO` para `PRONTO` e, em seguida, para `RETIRADO`.
- Impressão de etiqueta e comprovante gerados com sucesso.

## Testes Recomendados
- Casos de unidade cobrindo autenticação, criação de pedido e geração de etiquetas.
- Testes de integração com PostgreSQL validando persistência de pedido, volume e evento.
- Testes funcionais cobrindo fluxo completo: cadastro, alocação, retirada e emissão de comprovante.

## Validações em Python
- `python -m pytest tests/fase_01/test_autenticacao.py` para cobrir login, perfis e regras de sessão.
- `python -m pytest tests/fase_01/test_fluxo_pedidos.py` para garantir criação de pedido, alocação de posição e transições de status.
- `python scripts/validadores/emitir_documentos.py --fase 1` para validar geração de PDFs de etiqueta e comprovante.
