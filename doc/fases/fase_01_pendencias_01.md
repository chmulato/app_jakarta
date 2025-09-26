Fase 1 Pendencias

Fundamentos
- [x] Projeto multi-modulos (api, web, core, persistence) estruturado no Maven (parent + submodulos).
- [x] Logging estruturado habilitado (JsonLayout + trace_id via TraceIdFilter).
- [x] Endpoint de health/readiness publicado em /api/health e /api/health/ready.
- [x] RBAC com perfis ADMIN, SUPERVISOR e OPERADOR atualizado em codigo e migracoes.

Banco de Dados
- [x] Indice para tenant_id em pedido criado (V1__fase1_schema.sql e V1_2__ajustes_rbac_indices.sql).

Interface JSP
- [x] Tela retirar.jsp adicionada em retirada/ para concluir fluxo pronto -> retirado.

Documentos PDF
- [x] PdfService com geracao de etiqueta e comprovante usando PDFBox.

Testes Python
- [ ] Suite pytest (tests/test_auth.py, tests/test_pedidos.py, etc.) continua ausente.


