# Guia de Deploy (Tomcat / WildFly)

## Objetivo
Resumo operacional para realizar build e deploy da aplicação usando Maven ou o orquestrador Python (`main.py`).

## Visão Rápida
| Item | Tomcat | WildFly |
|------|--------|---------|
| Porta HTTP | 9090 | 8080 |
| Porta Admin | - | 9990 |
| Diretório Base | `server/apache-tomcat-10.1.35` | `server/wildfly-37.0.1.Final` |
| Plugin Maven | `tomcat10-maven-plugin` | `wildfly-maven-plugin` |
| Perfil Maven | `tomcat` | `wildfly` |
| Deploy Via Script | Menu opção 2 / 3 | Menu opção 4 / 5 |

## Pré-Requisitos
Valide o ambiente antes do primeiro deploy:
```powershell
java -version
mvn -version
python --version
docker --version
```
Para banco (PostgreSQL via Docker):
```powershell
docker compose up -d postgres
docker ps --filter "name=postgres"
```
Se estiver em Windows e usando Docker Desktop, certifique-se de que o daemon iniciou (o comando abaixo deve responder sem erro):
```powershell
docker info
```
Se precisar do backend WSL2 e ele não existir:
```powershell
wsl --install
```
Após instalar WSL ou iniciar Docker Desktop, revalide com o script de setup (abaixo).

### Scripts de setup (recomendado)
```powershell
./setup-python.ps1           # Cria venv e instala requirements
python .\setup.dev.py --only-check   # Checagens
python .\setup.dev.py --auto-fix     # (opcional) corrige o que for possível
```
Principais verificações: Java, Maven, Docker CLI + daemon, container postgres, perfis Maven, virtualenv Python, bcrypt.

## Validação de Ambiente / Fluxo com Script Python
Antes de qualquer deploy:
```powershell
python .\main.py --only-check
```
Fluxo geral:
```powershell
python .\main.py        # Abre menu interativo
python .\main.py --only-check   # Apenas valida ambiente
python .\main.py --tomcat-dir C:\srv\tomcat10 --wildfly-dir D:\srv\wf37
```
Logs de execução Maven/Deploy: `log/maven_deploy.log`.

### Execução fim a fim (Opção 12)
Fluxo completo e não interativo: para servidores ativos, prepara DB e seed (BCrypt $2a$), builda, faz deploy (Tomcat cold deploy / WildFly hot deploy), valida JNDI (com nomes distintos por servidor) e testa login.

```powershell
python .\main.py 12
```

## Build Manual
```powershell
# Tomcat (sem testes)
mvn clean package -Ptomcat -DskipTests
# WildFly (sem testes)
mvn clean package -Pwildfly -DskipTests
# Execução rápida (perfil run)
mvn -Prun
```

## Deploy Tomcat
```powershell
# Build + run (plugin)
mvn clean package tomcat10:run -Ptomcat -DskipTests -Dmaven.tomcat.port=9090
# Já empacotado
target/caracore-hub.war -> copiar para webapps/ (se usar standalone)
```
Via script: Menu → Opção 2 (deploy) / Opção 3 (iniciar sem deploy).

Notas:
- JNDI no Tomcat: `java:comp/env/jdbc/PostgresDS` (configurado em `conf/context.xml` usando factory DBCP do Tomcat).
- Contexto: acesso padrão `http://localhost:9090/caracore-hub`. Se publicado como `ROOT.war`, o acesso será `http://localhost:9090/`.

## Deploy WildFly
```powershell
# Build + deploy
mvn clean package -Pwildfly wildfly:deploy -DskipTests
# Undeploy
mvn wildfly:undeploy -Pwildfly
```
Via script: Menu → Opção 4 (deploy) / Opção 5 (iniciar sem deploy).

Notas:
- JNDI no WildFly: `java:/jdbc/PostgresDS` (configurado em `standalone.xml`).
- Contexto: acesso padrão `http://localhost:8080/caracore-hub`. Se publicado como `ROOT.war`, o contexto será `/` (http://localhost:8080/).

## Overrides de Diretório
Precedência: argumento > variável ambiente > padrão.
| Tipo | Chave | Exemplo |
|------|-------|---------|
| ENV | `APP_TOMCAT_DIR` | `C:\servers\tomcat10` |
| ENV | `APP_WILDFLY_DIR` | `D:\infra\wildfly` |
| ARG | `--tomcat-dir` | `--tomcat-dir C:\custom\tomcat` |
| ARG | `--wildfly-dir` | `--wildfly-dir D:\custom\wf` |

## Limpeza / Undeploy
Script: Opção 6 (limpa `target/`, WARs em `webapps/`, `deployments/`, caches, diretórios temporários).
Manual:
```powershell
mvn clean
# Tomcat
Remove-Item .\server\apache-tomcat-10.1.35\webapps\*.war -Force -ErrorAction SilentlyContinue
# WildFly
Remove-Item .\server\wildfly-37.0.1.Final\standalone\deployments\* -Force -ErrorAction SilentlyContinue
```

## Diagnóstico
| Ação | Tomcat | WildFly |
|------|--------|---------|
| Logs | `logs/catalina.*` | `standalone/log/server.log` |
| Porta | 9090 | 8080 / 9990 |
| Diagnóstico Script | Opção 7 | Opção 8 |

## Testes + Cobertura
```powershell
mvn clean test verify
Start-Process .\caracore-hub\target\site\jacoco\index.html
```

## Troubleshooting / Erros Comuns
| Problema | Causa Provável | Ação Rápida |
|----------|----------------|-------------|
| Porta 9090 (Tomcat) ocupada | Processo anterior / outro serviço | `netstat -ano | findstr :9090` + `taskkill /PID <PID> /F` |
| Porta 8080 (WildFly) ocupada | Instância anterior ativa | Mesmo procedimento acima usando :8080 |
| Docker instalado mas falha `docker info` | Daemon não iniciado | Abrir Docker Desktop e aguardar estabilização |
| WSL ausente (necessário p/ backend Docker) | WSL não instalado | `wsl --install` e reiniciar, depois `./setup-dev.ps1 -OnlyCheck` |
| WAR não gerado | Falha de build Maven | Checar `log/maven_deploy.log` e saída do Maven |
| Deploy WildFly pendente | Exploração de dependências lenta / conflito | Ver `standalone/log/server.log`; limpar `standalone/tmp` e `standalone/data` |
| JDBC falha (timeout / auth) | Container não iniciou ou credenciais | `docker ps`, conferir URL em `persistence.xml` |
| JSTL erro de URI | URIs antigas (`http://java.sun.com/...`) | Usar taglibs Jakarta (`jakarta.tags.core`) e dependências JSTL 3.x |
| Overrides ignorados | Ordem de precedência desconhecida | Confirmar: argumento CLI > variável ambiente > padrão |
| Perfis Maven não encontrados | Perfil ausente no `pom.xml` | Verificar seção `<profiles>` e nome exato |

Mais casos detalhados em `TESTES-RELATORIO.md` e seção de diagnóstico do menu Python.

## Próximas Melhorias Sugeridas
- Passar portas via argumentos (`--tomcat-port`, `--wildfly-port`)
- Relatório consolidado pós-deploy
- Automação CI para build + testes + deploy opcional

---
Documento focado em execução operacional. Para detalhes arquiteturais ver `ARQUITETURA.md`. Histórico de refatoração em `REFATORACAO.md`.

---
Notas:
1. Parametrização de portas ainda não exposta via CLI; alteração hoje requer ajuste no código (`main.py`).
2. O uso dos plugins Maven pode variar conforme futuras otimizações de tempo de build.
3. Em ambientes CI futuros, parte deste fluxo será automatizada (ver lista de melhorias).
