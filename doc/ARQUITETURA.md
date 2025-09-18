docker-compose up -d postgres
mvn clean compile -Ptomcat exec:java
mvn clean package -Pwildfly wildfly:deploy
# Arquitetura Atualizada do Projeto

## 📋 Visão Geral
O projeto utiliza um script Python (`main.py`) como orquestrador central para:
1. Verificação de ambiente (Java, Maven, Docker, PostgreSQL)
2. Build e empacotamento (perfis Maven `tomcat`, `wildfly`, `run`)
3. Deploy e diagnóstico em Tomcat ou WildFly
4. Limpeza de artefatos e análise básica de runtime

Os servidores agora ficam em `server/`:
```
server/
 ├── apache-tomcat-10.1.35/
 └── wildfly-37.0.1.Final/
```

## 🗂️ Estrutura de Código Aplicação
```
meu-projeto-java/
 ├── pom.xml
 └── src/
   ├── main/java/com/exemplo/
   │   ├── servlet/      # Servlets e camadas web
   │   ├── dao/          # Acesso a dados
   │   ├── model/        # Entidades / modelos
   │   └── config/       # Configurações gerais (se houver)
   ├── main/resources/
   └── main/webapp/      # JSP / WEB-INF / recursos estáticos
```

Não há mais pacote `server/` com classes abstratas específicas; o controle de execução está encapsulado no fluxo Maven + script Python.

## ⚙️ Portas Padrão (Atual)
| Servidor | Porta HTTP | Porta Administração | Origem Configuração |
|----------|------------|---------------------|--------------------|
| Tomcat   | 9090       | N/A                 | Constante `TOMCAT_PORT` em `main.py` + `server.xml` ajustado dinamicamente |
| WildFly  | 8080       | 9990                | WildFly `standalone.xml` (socket-binding) + constantes em `main.py` |

## 🔧 Overrides de Diretórios
Precedência: argumento CLI > variável ambiente > padrão.
| Tipo | Chave | Exemplo |
|------|------|---------|
| ENV  | `APP_TOMCAT_DIR` | `C:\servers\tomcat10` |
| ENV  | `APP_WILDFLY_DIR` | `D:\infra\wildfly` |
| ARG  | `--tomcat-dir` | `--tomcat-dir C:\custom\tomcat` |
| ARG  | `--wildfly-dir` | `--wildfly-dir D:\wf` |

## 🧩 Perfis Maven
| Perfil | Objetivo |
|--------|----------|
| `tomcat` | Build para deploy em Tomcat (WAR + plugin tomcat10) |
| `wildfly` | Build alinhado às bibliotecas do WildFly |
| `run` | Execução rápida (Tomcat incorporado / desenvolvimento) |

## 🚀 Fluxo de Deploy (Tomcat)
1. `mvn clean package -Ptomcat -DskipTests`
2. Ajuste da porta em `conf/server.xml` (se necessário)
3. WAR copiado para `webapps/`
4. Inicialização (plugin ou startup script)

## 🏢 Fluxo de Deploy (WildFly)
1. `mvn clean package -Pwildfly -DskipTests`
2. WAR para `standalone/deployments/`
3. Inicialização (plugin ou `standalone.bat`)

## 🛠️ Papel do Script `main.py`
| Função | Detalhe |
|--------|---------|
| Verificação | Java / Maven / Docker / DB / perfis pom |
| Build | Usa `execute_maven_command()` centralizado |
| Deploy | Tomcat ou WildFly com opções interativas |
| Diagnóstico | Leitura de logs, checagem de portas |
| Limpeza | Remove WARs, `target/`, caches e diretórios temporários |

## 🔍 Diagnóstico Integrado
Funções: `diagnose_tomcat_issues()` e `diagnose_wildfly_issues()` examinam logs finais, estrutura de diretórios e portas em uso, sugerindo ações.

## �️ Princípios Mantidos
| Princípio | Aplicação Atual |
|-----------|-----------------|
| Minimizar acoplamento | Servidores tratados externamente via script |
| Configuração externa | Portas e caminhos podem ser sobrescritos |
| Observabilidade | Log unificado em `log/maven_deploy.log` |
| Reprodutibilidade | Build determinístico via perfis Maven |

## 🔄 Evolução em Relação à Versão Anterior
- Removidas referências a classes `AbstractWebServer`, `WebServerInterface` (não presentes na árvore atual)
- Centralização operacional no script Python em vez de lógica Java embarcada
- Portas invertidas (Tomcat agora 9090 para evitar conflito com WildFly 8080)
- Simplificação: Foco em WAR padrão + plugin ou servidor standalone

## � Possíveis Extensões Futuras
- Parametrização de portas via argumentos (`--tomcat-port`, `--wildfly-port`)
- Geração automatizada de relatório de cobertura consolidado
- Endpoint de health-check simples

## ✅ Resumo
Arquitetura simplificada, centrada em automação externa, com flexibilidade para múltiplos ambientes e fácil evolução.