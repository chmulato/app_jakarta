## 🚀 Projeto Java Web (Tomcat / WildFly)

Aplicação Java (Jakarta EE) com autenticação, deploy via Tomcat ou WildFly, automação em Python e banco PostgreSQL em Docker.

—

### Visão Geral
- Código da aplicação: `meu-projeto-java`
- Automação: `main.py` (menu + build/deploy/diagnóstico)
- Banco de dados: `docker-compose.yml` + `docker/postgres/init/01-init.sql`
- Servidores suportados:
  - Tomcat 10.1.35 (HTTP 9090 quando standalone via `main.py`)
  - WildFly 37.0.1.Final (HTTP 8080, Management 9990)

—

### Pré‑requisitos
- Java JDK 11+ (recomendado definir `JAVA_HOME`)
- Maven 3.8+
- Docker Desktop (Compose v2) para banco local
- Python 3.10+ (para `main.py` e `setup.dev.py`)

Teste rápido (PowerShell):
```powershell
java -version
mvn -version
python --version
docker --version
```

—

### Setup Rápido
1) Validar e preparar ambiente (venv, Maven, Docker, Postgres):
```powershell
python .\setup.dev.py                # Checagens + cria venv e instala requirements
python .\setup.dev.py --only-check   # Apenas checagens (sem modificar nada)
python .\setup.dev.py --auto-fix     # Tenta corrigir problemas (Docker, Maven, Postgres, bcrypt)
```
2) Ativar a venv (opcional, para rodar `main.py` no ambiente isolado):
```powershell
. .\.venv\Scripts\Activate.ps1
```
3) Subir o Postgres em Docker (se ainda não estiver rodando):
```powershell
docker compose up -d postgres
docker ps --filter "name=postgres"
```
4) Fazer uma checagem final do projeto:
```powershell
python .\main.py --only-check
```

Alternativa curta apenas para Python: `./setup-python.ps1` (cria venv e instala requirements).

—

### Execução (menu Python)
Abra o menu interativo e siga as opções de build/deploy/diagnóstico:
```powershell
python .\main.py
```

Overrides de diretório dos servidores (precedência: argumento > variável > padrão):
```powershell
$env:APP_TOMCAT_DIR='C:\servers\tomcat10'
$env:APP_WILDFLY_DIR='C:\servers\wildfly37'
python .\main.py

# ou via argumentos
python .\main.py --tomcat-dir C:\servers\tomcat10 --wildfly-dir D:\wildfly-37
```
Logs do orquestrador: `log/maven_deploy.log`.

—

### Banco de Dados
- Serviço: `postgres` em `docker-compose.yml`
- Inicialização: scripts em `docker/postgres/init/*.sql`
- Credenciais/DB padrão (compose): `meu_app_db` / `meu_app_user` / `meu_app_password`

Usuários padrão criados no primeiro start (hash BCrypt de `Admin@123`):
- admin@meuapp.com (ADMIN)
- admin@exemplo.com (ADMIN)
- joao@exemplo.com (USUARIO)
- maria@exemplo.com (USUARIO)

Validação do hash do admin (instala `bcrypt` se faltar):
```powershell
python .\setup.dev.py --auto-fix
```

—

### Build e Testes (Maven)
```powershell
cd .\meu-projeto-java
mvn clean package -DskipTests
mvn clean test verify
```
Perfis disponíveis no `pom.xml`: `tomcat`, `wildfly`, `run`.
```powershell
mvn clean package -Ptomcat -DskipTests
mvn clean package -Pwildfly -DskipTests
mvn -Prun
```

—

### Deploy
Tomcat (recomendado via `main.py`):
- Empacota WAR, configura `server.xml` para porta 9090 e copia como `ROOT.war` para `webapps/` do Tomcat standalone.
- Acesso: http://localhost:9090/

Tomcat (plugin Maven de desenvolvimento):
```powershell
mvn -f .\meu-projeto-java\pom.xml tomcat10:run -DskipTests
```
- Porta padrão do plugin: 8080 → http://localhost:8080/

WildFly:
- WAR enviado para `standalone/deployments` como `ROOT.war`.
- Acesso: http://localhost:8080/
- Console: http://localhost:9990/

Portas podem ser ajustadas no `main.py` (`TOMCAT_PORT`, `WILDFLY_PORT`) ou nas configurações dos servidores.

—

### Variáveis/Argumentos Úteis
- `APP_TOMCAT_DIR`: caminho do Tomcat
- `APP_WILDFLY_DIR`: caminho do WildFly
- `--tomcat-dir` / `--wildfly-dir`: overrides via CLI
- `--only-check`: somente validações e saída

—

### Troubleshooting Rápido
- Porta ocupada (8080/9090):
```powershell
netstat -ano | findstr :8080
taskkill /F /PID <PID>
```
- Docker inativo: abra o Docker Desktop e revalide.
- Compose v2: use `docker compose`, não `docker-compose`.
- Tomcat logs: `server/apache-tomcat-*/logs/`
- WildFly logs: `server/wildfly-*/standalone/log/server.log`
- JSTL (Jakarta): use dependências `jakarta.servlet.jsp.jstl-*` (já no `pom.xml`).

Documentação adicional: `doc/DEPLOY.md`, `doc/ARQUITETURA.md`, `doc/RESULTADOS-TESTES.md`.

—

### Estrutura (resumo)
```
app_jakarta/
 ├─ main.py
 ├─ docker-compose.yml
 ├─ docker/
 │   └─ postgres/init/01-init.sql
 ├─ meu-projeto-java/
 │   └─ pom.xml, src/
 ├─ server/ (criado se Tomcat/WildFly standalone forem usados)
 └─ log/ (maven_deploy.log)
```

—

### Licença e Suporte
- Uso interno/educacional. Defina licença se for público.
- Para suporte, anexe passos, `log/maven_deploy.log`, SO e versões (Java/Maven/Docker).

—

Última atualização: 18 de Setembro de 2025
