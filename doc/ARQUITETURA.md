# Arquitetura do Projeto (Atualizada)

> Veja também no README

- [Setup rápido](../README.md#readme-setup-rapido)
- [Deploy](../README.md#readme-deploy)
- [Datasource (PostgreSQL)](../README.md#readme-datasource)

## Visão geral

O projeto utiliza um script Python (`main.py`) como orquestrador central para:

1. Verificação de ambiente (Java, Maven, Docker, PostgreSQL)
2. Build e empacotamento (perfis Maven `tomcat`, `wildfly`, `run`)
3. Deploy e diagnóstico em Tomcat ou WildFly
4. Limpeza de artefatos e análise básica de runtime

Os servidores agora ficam em `server/`:

```text
server/
 ├── apache-tomcat-10.1.35/
 └── wildfly-37.0.1.Final/
```

## Estrutura de código da aplicação

```
caracore-hub/
 ├── pom.xml
 └── src/
   ├── main/java/com/caracore/hub_town/
   │   ├── servlet/      # Servlets e camada web (Login/Dashboard/Logout/AuthFilter)
   │   ├── dao/          # Acesso a dados (JPAUtil, UsuarioDAO)
   │   ├── model/        # Entidades (Usuario, Produto)
   │   └── config/       # Configurações auxiliares
   ├── main/resources/   # META-INF/persistence.xml
   └── main/webapp/      # JSP / WEB-INF / META-INF/context.xml
```

Não há mais pacote `server/` com classes abstratas específicas; o controle de execução está encapsulado no fluxo Maven + script Python.

## Portas padrão

| Servidor | Porta HTTP | Porta Administração | Origem Configuração |
|----------|------------|---------------------|--------------------|
| Tomcat   | 9090       | N/A                 | Constante `TOMCAT_PORT` em `main.py` + `server.xml` ajustado dinamicamente |
| WildFly  | 8080       | 9990                | WildFly `standalone.xml` (socket-binding) + constantes em `main.py` |

## Overrides de diretórios

Precedência: argumento CLI > variável ambiente > padrão.

| Tipo | Chave | Exemplo |
|------|------|---------|
| ENV  | `APP_TOMCAT_DIR` | `C:\servers\tomcat10` |
| ENV  | `APP_WILDFLY_DIR` | `D:\infra\wildfly` |
| ARG  | `--tomcat-dir` | `--tomcat-dir C:\custom\tomcat` |
| ARG  | `--wildfly-dir` | `--wildfly-dir D:\wf` |

## Perfis Maven

| Perfil | Objetivo |
|--------|----------|
| `tomcat` | Build para deploy em Tomcat (WAR + plugin tomcat10) |
| `wildfly` | Build alinhado às bibliotecas do WildFly |
| `run` | Execução rápida (Tomcat incorporado / desenvolvimento) |

## Fluxo de deploy (Tomcat)

1. `mvn clean package -Ptomcat -DskipTests`
2. Ajuste da porta em `conf/server.xml` (se necessário)
3. WAR copiado para `webapps/`
4. Inicialização (plugin ou startup script)

## Fluxo de deploy (WildFly)

1. `mvn clean package -Pwildfly -DskipTests`
2. WAR para `standalone/deployments/`
3. Inicialização (plugin ou `standalone.bat`)

## Papel do script `main.py`

| Função | Detalhe |
|--------|---------|
| Verificação | Java / Maven / Docker / DB / perfis pom |
| Build | Usa `execute_maven_command()` centralizado |
| Deploy | Tomcat ou WildFly com opções interativas |
| Diagnóstico | Leitura de logs, checagem de portas |
| Limpeza | Remove WARs, `target/`, caches e diretórios temporários |

## Diagnóstico integrado

Funções: `diagnose_tomcat_issues()` e `diagnose_wildfly_issues()` examinam logs finais, estrutura de diretórios e portas em uso, sugerindo ações.

## Princípios mantidos

| Princípio | Aplicação Atual |
|-----------|-----------------|
| Minimizar acoplamento | Servidores tratados externamente via script |
| Configuração externa | Portas e caminhos podem ser sobrescritos |
| Observabilidade | Log unificado em `log/maven_deploy.log` |
| Reprodutibilidade | Build determinístico via perfis Maven |

## Evolução em relação à versão anterior

- Removidas referências a classes `AbstractWebServer`, `WebServerInterface` (não presentes na árvore atual)
- Centralização operacional no script Python em vez de lógica Java embarcada
- Portas invertidas (Tomcat agora 9090 para evitar conflito com WildFly 8080)
- Simplificação: Foco em WAR padrão + plugin ou servidor standalone

## Possíveis extensões futuras

- Parametrização de portas via argumentos (`--tomcat-port`, `--wildfly-port`)
- Geração automatizada de relatório de cobertura consolidado
- Endpoint de health-check simples

## Padrões de integração (datasource e contexto)

- Datasource/JNDI: nomes distintos por servidor.
  - Tomcat: `java:comp/env/jdbc/PostgresDS` (definido em `conf/context.xml`, factory DBCP do Tomcat)
  - WildFly: `java:/jdbc/PostgresDS` (definido em `standalone.xml` com módulo JDBC do PostgreSQL)
- Contexto do WAR: padrão `/caracore-hub` via metadados do artefato (finalName); quando publicado como `ROOT.war`, o contexto efetivo passa a ser `/`.
- Orquestrador detecta e ajusta URLs conforme servidor/porta/contexto.

## Segurança e autenticação (seed)

- Usuário ADMIN semeado com senha `Admin@123` usando hash BCrypt com prefixo `$2a$` (compatível com jBCrypt em Java).
- O orquestrador normaliza hashes `$2b$` → `$2a$` quando necessário para compatibilidade.

## Execução fim a fim

- Opção 12 do `main.py` realiza: parada preventiva de servidores, preparação do banco, seed de usuário, build, deploy nos dois servidores, validações de JNDI e teste automático de login.

## Pacotes e unidade de persistência

- Pacote base Java: `com.caracore.hub_town`
- Unidade de persistência (PU): `meuAppPU`
- Entidades mapeadas: `Usuario`, `Produto` (Produto com campos `descricao` e `preco` BigDecimal)

## Resumo

Arquitetura simplificada, centrada em automação externa, com flexibilidade para múltiplos ambientes e fácil evolução.