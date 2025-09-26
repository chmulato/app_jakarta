# Fase 1 · Pendências (Atualização 02)

## Resumo rápido
- Pipeline "Opção 12" está rodando fim-a-fim (build → deploy Tomcat/WildFly → login) após correções de JSTL e variáveis de ambiente.
- Persistem avisos críticos ligados ao banco e à validação do datasource do WildFly.
- Cobertura de testes automatizados para a fase 1 ainda não foi iniciada.

## Pendências prioritárias

### 1. Banco de Dados · coluna `evento.payload`
- [ ] Ajustar tipo para `oid` ou revisar mapeamento Hibernate.
- Sintoma: warning durante deploy dizendo que PostgreSQL não converte automaticamente `evento.payload` em `oid`.
- Ação recomendada: criar migração `ALTER TABLE evento ALTER COLUMN payload TYPE oid USING payload::oid;` e validar com Option 12.
- Responsável sugerido: Squad Persistência.

### 2. WildFly · teste CLI do datasource
- [ ] Timeout do script de verificação JBoss CLI após 30s, apesar do datasource subir.
- Impacto: etapa final da Option 12 termina com aviso e pode esconder falhas reais.
- Ação recomendada: revisar `standalone.xml` (alterações recentes) e aumentar timeout ou simplificar o comando de teste.
- Responsável sugerido: Squad Plataforma.

### 3. Testes Python para Fase 1
- [ ] Criar suíte pytest cobrindo autenticação e fluxo de pedidos.
- Escopo mínimo: `test_autenticacao.py` e `test_fluxo_pedidos.py` conforme guia da fase.
- Bloqueio atual: inexistência de fixtures para banco; sugerido reutilizar containers Docker.
- Responsável sugerido: QA / Automação.

## Melhorias complementares
- [ ] Tratar warning do Hibernate sobre `hibernate.dialect` deprecated (configurar `jakarta.persistence.jdbc.url` e dialeto novo).
- [ ] Atualizar documentação de deploy após ajustes no WildFly.

## Próximos passos sugeridos
1. Rodar migração do banco e validar Option 12 novamente para confirmar eliminação do warning.
2. Ajustar script/timeout do datasource e repetir Option 12 observando logs do WildFly.
3. Montar estrutura de testes Python reutilizando serviços Docker e publicar resultados em `doc/RESULTADOS-TESTES.md`.
