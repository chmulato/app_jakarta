# Fase 1 · Pendências (Atualização 02)

## Resumo rápido
- Pipeline "Opção 12" está rodando fim-a-fim (build → deploy Tomcat/WildFly → login) após correções de JSTL e variáveis de ambiente.
- Persistem avisos críticos ligados ao banco e à validação do datasource do WildFly.
- Cobertura de testes automatizados para a fase 1 ainda não foi iniciada.

## Pendências prioritárias

### 1. Banco de Dados · coluna `evento.payload`
- [x] Ajustar tipo para `oid` ou revisar mapeamento Hibernate.
- Sintoma: warning durante deploy dizendo que PostgreSQL não converte automaticamente `evento.payload` em `oid`.
- Ação executada: adicionada migração `V1_3__evento_payload_text.sql` para usar `TEXT` (compatível com JSON) e mapeamento Hibernate atualizado (`columnDefinition="text"`). Warnings de cast não ocorrem mais na Option 12.
- Responsável sugerido: Squad Persistência.

### 2. WildFly · teste CLI do datasource
- [x] Timeout do script de verificação JBoss CLI após 30s, apesar do datasource subir.
- Impacto: etapa final da Option 12 termina com aviso e pode esconder falhas reais.
- Ação executada: `main.py` agora só invoca `jboss-cli` quando variáveis `APP_WILDFLY_CLI_USER/APP_WILDFLY_CLI_PASSWORD` estão presentes; caso contrário registra mensagem informativa e segue com a validação pelos logs. Evita travar em ambientes sem usuário ManagementRealm.
- Responsável sugerido: Squad Plataforma.

### 3. Testes Python para Fase 1
- [ ] Criar suíte pytest cobrindo autenticação e fluxo de pedidos.
- Escopo mínimo: `test_autenticacao.py` e `test_fluxo_pedidos.py` conforme guia da fase.
- Bloqueio atual: inexistência de fixtures para banco; sugerido reutilizar containers Docker.
- Responsável sugerido: QA / Automação.

## Melhorias complementares
- [x] Tratar warning do Hibernate sobre `hibernate.dialect` deprecated (configurar `jakarta.persistence.jdbc.url` e dialeto novo).
- [ ] Atualizar documentação de deploy após ajustes no WildFly.

## Próximos passos sugeridos
1. Monitorar Option 12 após configurar credenciais de management do WildFly (se desejado) para validar `jboss-cli` de ponta a ponta.
2. Montar estrutura de testes Python reutilizando serviços Docker e publicar resultados em `doc/RESULTADOS-TESTES.md`.
3. Atualizar documentação de deploy com as novas variáveis de ambiente (`APP_WILDFLY_CLI_USER/APP_WILDFLY_CLI_PASSWORD`) e a migração `V1_3__evento_payload_text.sql`.
