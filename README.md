<div align="center">

# 🚀 Projeto Java Web (Tomcat / WildFly) 
Sistema web Java (Jakarta EE) com autenticação, deploy fl- **Correção automática:** Se `bcrypt` estiver faltando, o script instala automaticamente e revalida o hash.

### Verificações de Ambiente Detalhadas
- **Java/Maven:** Versões mínimas e disponibilidade de comandos
- **Docker:** Daemon ativo e containers PostgreSQL em execução
- **Banco de dados:** Conectividade e estrutura da tabela `usuarios`
- **Dependências Python:** Instalação automática de pacotes críticos (`bcrypt`, `psycopg2`, etc.)
- **Servidores:** Estrutura de diretórios Tomcat/WildFly e arquivos de configuração

### Scripts de Setup Detalhados

#### `setup.dev.py` - Setup Completo Python
Script principal para configuração completa do ambiente Python:
```powershell
python setup.dev.py              # Setup completo com validações
python setup.dev.py --auto-fix   # Correção automática de problemas detectados
python setup.dev.py --only-check # Apenas validação (sem modificações)
```

**Funcionalidades:**
- Criação e configuração de ambiente virtual Python
- Instalação automática de dependências críticas (`bcrypt`, `psycopg2-binary`, `colorama`, etc.)
- Validação completa de ambiente (Java, Maven, Docker, PostgreSQL)
- **Validação de hash BCrypt do admin** com detecção automática de problemas
- Verificação de conectividade com banco de dados
- Relatórios detalhados de status do ambiente

#### `setup-python.ps1` - Setup Focado Python (PowerShell)
Script PowerShell alternativo para setup mais leve:
```powershell
./setup-python.ps1         # Setup completo Python
./setup-python.ps1 -Force  # Recria ambiente virtual
./setup-python.ps1 -OnlyCheck # Apenas validação
```

**Funcionalidades:**
- Criação de ambiente virtual Python via PowerShell
- Instalação automática de dependências Python críticas
- Verificação de instalação do Python e pip
- Integração com sistema de requirements.txt
- Validação de ambiente Python específico

---

## 🔧 Variáveis de Ambiente (Overrides)mcat ou WildFly, automação via script Python e integração PostgreSQL.

</div>

---

## 📌 Visão Geral
Este repositório reúne:
1. Código da aplicação (`meu-projeto-java`)
2. Automação de build/deploy via `main.py` (substitui scripts PowerShell isolados)
3. Infraestrutura de banco (PostgreSQL via Docker Compose)
4. Suporte a dois modelos de execução:
    - Tomcat (porta padrão configurável, usamos 9090 para evitar conflito com WildFly 8080)
    - WildFly (porta HTTP 8080 e management 9990)

---

## 🧱 Tecnologias Principais
| Camada | Tecnologias |
|--------|-------------|
| Linguagem | Java 11+ |
| Plataforma | Jakarta Servlet 6 / Jakarta EE APIs necessárias |
| Build | Maven |
| Servidores | Apache Tomcat 10.1.35 / WildFly 37.0.1.Final |
| Banco | PostgreSQL (Docker) |
| Pool | HikariCP |
| Persistência | Hibernate ORM |
| View | JSP + JSTL + Bootstrap 5 |
| Cobertura | JaCoCo |

---

## 🗂️ Estrutura Simplificada
```
app_jakarta/
 ├── main.py                  # Script Python (menu + automação deploy/testes)
 ├── docker-compose.yml       # Serviços (PostgreSQL)
 ├── server/
 │   ├── apache-tomcat-10.1.35/
 │   └── wildfly-37.0.1.Final/
 ├── meu-projeto-java/
 │   ├── pom.xml
 │   └── src/...
 └── log/                     # Logs do script (maven_deploy.log)
```

---

## ✅ Pré-requisitos
| Item | Versão recomendada | Observação |
|------|--------------------|------------|
| Java (JDK) | 11+ | Necessário `JAVA_HOME` ou detecção automática |
| Maven | 3.8+ | Usado para build/test/deploy |
| Docker Desktop | Atual | Apenas se quiser PostgreSQL local via compose |
| PowerShell ou Bash | Qualquer | Execução de comandos auxiliares |
| Python | 3.10+ | Para usar `main.py` |

Teste rápido (PowerShell):
```powershell
java -version
mvn -version
python --version
docker --version
```

---

## ⚡ Setup Rápido (Automatizado)
Para preparar o ambiente (checagens + venv + dependências Python):
```powershell
# Setup completo (inclui verificações Java, Maven, Docker, banco)
./setup-dev.ps1            # Executa checagens e cria venv se necessário
./setup-dev.ps1 -OnlyCheck # Apenas valida ambiente
./setup-dev.ps1 -Force     # Recria venv
./setup-dev.ps1 -SkipPython # Pula parte Python

# Apenas setup Python (alternativa mais leve e focada)
./setup-python.ps1         # Cria venv e instala dependências Python
./setup-python.ps1 -OnlyCheck # Apenas valida ambiente Python
./setup-python.ps1 -Force  # Recria venv
./setup-python.ps1 -Python python3 # Especifica executável Python
```
Após execução bem-sucedida:
```powershell
.\.venv\Scripts\Activate.ps1
python .\main.py --only-check
```
Principais verificações: 
 - Java / Maven (versões e disponibilidade)
 - Docker CLI + Docker daemon ativo (`docker info`)
 - Container PostgreSQL (nome contendo `postgres`)
 - WSL (presença de `wsl.exe` e distros registradas) em ambientes Windows
 - Perfis Maven esperados (`tomcat`, `wildfly`, `run`)
 - Virtualenv Python + libs (`requests`, `colorama`, `psutil`, `bcrypt`)
 - **Validação de hash do admin** (verifica integridade das credenciais padrão no banco)

**Funcionalidades avançadas dos scripts de setup:**
- **Instalação automática de dependências críticas**: Scripts detectam e instalam automaticamente bibliotecas Python necessárias (como `bcrypt` para validação de hash)
- **Validação de ambiente completa**: Inclui verificação de conectividade com banco PostgreSQL e validação de hash das credenciais admin
- **Correção automática**: Opção `--auto-fix` em `setup.dev.py` permite correção automática de problemas detectados

Se o Docker estiver instalado mas o daemon desligado: iniciar Docker Desktop e reexecutar `./setup-dev.ps1 -OnlyCheck`.
Se WSL não estiver instalado e pretende usar backend WSL2 do Docker: `wsl --install` (requer reboot).

---

## � Validação de Ambiente
Os scripts de setup realizam validação completa do ambiente de desenvolvimento, incluindo verificação de conectividade com banco e integridade das credenciais.

### Validação de Hash do Admin
O script `setup.dev.py` inclui validação automática do hash BCrypt do usuário admin padrão:
- **Status possíveis:**
  - ✅ **OK**: Hash válido e corresponde à senha padrão
  - ❌ **MISMATCH**: Hash não corresponde (possível alteração manual)
  - ⚠️ **N/A**: Biblioteca `bcrypt` não disponível (será instalada automaticamente)

- **Funcionamento:**
  ```powershell
  python setup.dev.py --auto-fix  # Executa validação completa com correção automática
  ```

- **Correção automática:** Se `bcrypt` estiver faltando, o script instala automaticamente e revalida o hash.

### Verificações de Ambiente Detalhadas
- **Java/Maven:** Versões mínimas e disponibilidade de comandos
- **Docker:** Daemon ativo e containers PostgreSQL em execução
- **Banco de dados:** Conectividade e estrutura da tabela `usuarios`
- **Dependências Python:** Instalação automática de pacotes críticos (`bcrypt`, `psycopg2`, etc.)
- **Servidores:** Estrutura de diretórios Tomcat/WildFly e arquivos de configuração

---

## �🔧 Variáveis de Ambiente (Overrides)
O script `main.py` aceita overrides para diretórios dos servidores.

| Variável | Propósito | Exemplo |
|----------|----------|---------|
| `APP_TOMCAT_DIR` | Caminho alternativo do Tomcat | `C:\tools\tomcat10` |
| `APP_WILDFLY_DIR` | Caminho alternativo do WildFly | `D:\servers\wildfly-37` |

Também é possível passar por argumentos CLI (precedência: argumento > variável > padrão).

| Argumento | Exemplo |
|-----------|---------|
| `--tomcat-dir` | `--tomcat-dir C:\custom\tomcat` |
| `--wildfly-dir` | `--wildfly-dir D:\wf` |
| `--only-check` | Apenas verifica ambiente e sai |

---

## 🚀 Uso Rápido (Script Python)
Menu interativo:
```powershell
python .\main.py
```

Somente validar ambiente:
```powershell
python .\main.py --only-check
```

Com overrides de diretórios:
```powershell
$env:APP_TOMCAT_DIR='C:\servers\tomcat10'; $env:APP_WILDFLY_DIR='C:\servers\wildfly37'; python .\main.py
# ou via argumentos
python .\main.py --tomcat-dir C:\servers\tomcat10 --wildfly-dir C:\servers\wildfly37
```

Logs: `log/maven_deploy.log`

> Nota: Scripts PowerShell legados na pasta `scr/` foram substituídos pelo fluxo central via `main.py` e serão gradualmente removidos. Consulte `doc/DEPLOY.md` e `doc/ARQUITETURA.md` para detalhes atualizados.

---

## 🌐 Banco de Dados (PostgreSQL via Docker)
Subir serviço:
```powershell
docker-compose up -d postgres
```
Verificar container:
```powershell
docker ps --filter "name=postgres"
```
Scripts de inicialização: `docker/postgres/init/*.sql`

---

## 🔄 Build & Testes
Build padrão:
```powershell
cd meu-projeto-java
mvn clean package -DskipTests
```
Testes com cobertura:
```powershell
mvn clean test verify
```
Via script Python (usa heurísticas e log unificado):
```powershell
python .\main.py --only-check  # Verifica antes
```

---

## 📦 Deploy no Tomcat
Opção de menu: 2 (deploy) ou 3 (iniciar sem deploy).

Passos internos executados pelo script:
1. Verificação ambiente (Java/Maven)
2. `mvn clean package -Ptomcat -DskipTests`
3. Ajuste de porta em `conf/server.xml` (porta configurada = 9090 por padrão)
4. Cópia do WAR para `webapps/`
5. Inicialização (plugin Maven ou Tomcat standalone)

URL após subida:
```
http://localhost:9090/MEU-CONTEXTO (ou conforme nome do WAR)
```

Alterar porta: editar constante `TOMCAT_PORT` em `main.py` (ou melhorar futuramente com argumento CLI se necessário).

---

## 🏢 Deploy no WildFly
Opção de menu: 4 (deploy) ou 5 (iniciar sem deploy).

Fluxo:
1. `mvn clean package -Pwildfly -DskipTests`
2. WAR enviado para `standalone/deployments`
3. Inicialização via plugin Maven ou `standalone.bat`

URLs:
```
Aplicação: http://localhost:8080/<contexto>
Console Admin: http://localhost:9990/
```

Portas: alterar constantes `WILDFLY_PORT` e `WILDFLY_MANAGEMENT_PORT` em `main.py` (ou via ajuste de configuração manual do servidor).

---

## 🧹 Limpeza / Undeploy
Pelo menu: opção 6.
Aciona função `clean_all_deployments()` que remove:
- `target/`
- WARs em `webapps/` (Tomcat)
- Artefatos `deployments/` (WildFly)
- Diretórios temporários (`tomcat.8080`, `tmp`)

---

## 🛠️ Diagnóstico
Opções:
- 7: Diagnóstico Tomcat (logs + server.xml + variáveis)
- 8: Diagnóstico WildFly (logs + deployments + portas)

Scripts chamam funções: `diagnose_tomcat_issues()` e `diagnose_wildfly_issues()`.

---

## 🧪 Perfis Maven
Perfis esperados no `pom.xml`:
| Perfil | Propósito |
|--------|----------|
| `tomcat` | Build para deploy em Tomcat |
| `wildfly` | Build alinhado às libs do WildFly |
| `run` | Execução com Tomcat embarcado / desenvolvimento rápido |

Exemplos:
```powershell
mvn clean package -Ptomcat -DskipTests
mvn clean package -Pwildfly -DskipTests
mvn -Prun
```

---

## 🔍 Troubleshooting Resumido
| Problema | Ação Rápida |
|----------|-------------|
| Porta 8080 ou 9090 ocupada | `netstat -ano | findstr :8080` / `:9090` + `taskkill /PID <PID> /F` |
| Docker instalado mas inativo | Abrir Docker Desktop até ícone estabilizar, depois `./setup-dev.ps1 -OnlyCheck` |
| WSL ausente (precisa para Docker) | `wsl --install` e reiniciar; depois revalidar |
| Tomcat não sobe | Ver `server/apache-tomcat-*/logs/` + opção 7 diagnóstico |
| WildFly travando | `server/wildfly-*/standalone/log/server.log`; limpar `standalone/tmp` e `standalone/data` |
| WAR não gerado | Conferir `mvn clean package -Ptomcat` saída e existência de `target/*.war` |
| JDBC falha | Ver container postgres (`docker ps`) + credenciais em `persistence.xml` |
| JSTL erro URI | Usar URIs `jakarta.tags.*` e dependências JSTL 3.x |
| **AdminHash mostra N/A** | `python setup.dev.py --auto-fix` (instala bcrypt automaticamente) |
| **AdminHash mostra MISMATCH** | Verificar se senha do admin foi alterada no banco; reset se necessário |

Material detalhado: ver `doc/TESTES-RELATORIO.md`, `doc/DEPLOY.md` e `doc/ARQUITETURA.md`.

---

## 🔒 Autenticação (Resumo)
Fluxo simples baseado em tabela `usuarios` (ver scripts SQL iniciais). Expansões futuras: autorização granular, recuperação de senha e auditoria.

### 🔐 Credenciais Padrão (Primeiro Acesso)
Criadas pelo script `docker/postgres/init/01-init.sql` apenas quando o banco é inicializado pela primeira vez ou não existe nenhum ADMIN.

| Tipo | Email | Senha (texto) | Perfil | Observações |
|------|-------|---------------|--------|-------------|
| Admin Principal | `admin@meuapp.com` | `Admin@123` | ADMIN | Usuário default documentado (hash BCrypt custo 10) |
| Admin Demo | `admin@exemplo.com` | `Admin@123` | ADMIN | Conta adicional de demonstração |
| Usuário Demo 1 | `joao@exemplo.com` | `Admin@123` | USUARIO | Dados de exemplo |
| Usuário Demo 2 | `maria@exemplo.com` | `Admin@123` | USUARIO | Dados de exemplo |

Avisos:
- Todas as contas usam o mesmo hash BCrypt para simplificar testes locais. NÃO reutilizar em produção.
- Altere a senha do `admin@meuapp.com` imediatamente em ambientes compartilhados.
- O bloco DO $$ é idempotente (não recria se já houver ADMIN).

Gerar novo hash (exemplo Python):
```python
import bcrypt; print(bcrypt.hashpw(b'NovaSenhaSegura!', bcrypt.gensalt(rounds=10)).decode())
```

Atualizar senha no banco:
```sql
UPDATE usuarios SET senha = '<NOVO_HASH_BCRYPT>' WHERE email = 'admin@meuapp.com';
```

### 🌐 Páginas e Rotas

| Rota | Arquivo / Origem | Perfil | Descrição |
|------|------------------|--------|-----------|
| `/` | `index.jsp` | Público | Landing page / marketing |
| `/login` | `login.jsp` | Público | Autenticação |
| `/dashboard` | `dashboard.jsp` | Autenticado | Painel principal |
| `/usuarios` | (Servlet) | ADMIN | Gestão de usuários |
| `/produtos` | (Servlet) | ADMIN / USUARIO(leitura) | Gestão de produtos |
| `/relatorios` | (Servlet) | ADMIN | Relatórios e métricas |
| `/configuracoes` | (Servlet) | ADMIN | Configurações administrativas |
| `/logout` | (Servlet) | Autenticado | Encerra sessão |
| `/test.jsp` | `test.jsp` | Público | Verificação básica do container |

Rotas sugeridas futuras: `/api/*`, `/health`, `/actuator/*` (se adicionar camada REST / observabilidade).

---

## 📁 Estrutura (Detalhada)
Para visão mais completa ver pasta `doc/`. Resumo principal permanece condizente com automação Python.

---

## ♻️ Próximas Melhorias Sugeridas
- Parametrizar portas via CLI (`--tomcat-port`, `--wildfly-port`)
- Adicionar coleta de métricas básicas (tempo de build, tamanho WAR)
- Exportar relatório de cobertura integrado (ler JaCoCo e sumarizar)
- Pipeline CI (GitHub Actions) com build + testes + deploy opcional

---

## 📄 Licença
Uso interno / educacional. Definir licença formal se for público.

---

## 🙋 Suporte
Abra uma issue descrevendo:
1. Passos executados
2. Saída relevante do log `log/maven_deploy.log`
3. Sistema operacional e versões (Java/Maven)

---

> Documentação atualizada para refletir novo fluxo via `main.py`, overrides de diretórios e diagnóstico unificado.

## 🔑 Funcionalidades Principais

- ✅ **Login Seguro** - Autenticação com email e senha
- ✅ **Controle de Sessão** - Gerenciamento de usuários logados
- ✅ **Dashboard Administrativo** - Interface para usuários autenticados
- ✅ **Layout Responsivo** - Funciona em dispositivos móveis e desktop

docker-compose up -d postgres
## 🚀 Execução (Atalho)
Use o menu Python (recomendado):
```powershell
python .\main.py
```
Deploy Tomcat direto:
```powershell
python .\main.py --only-check && mvn -f meu-projeto-java\pom.xml clean package -Ptomcat -DskipTests
```
Material comparativo e fluxo detalhado: ver `doc/DEPLOY.md`.

### Acessar a Aplicação
- **URL**: http://localhost:8080/meu-projeto-java/
- **Login**: http://localhost:8080/meu-projeto-java/login
- **Credenciais (demo)**: admin@meuapp.com / Admin@123

## 🧰 Scripts Legados
Ainda existem scripts PowerShell em `scr/`, porém a estratégia oficial é centralizar via `main.py`. Scripts legados serão revisados ou removidos em etapas futuras. Consulte `doc/REFATORACAO.md` para histórico.

## 🔍 Solução de Problemas (Detalhada)

### Problemas com Servidor Tomcat

1. **Porta 8080 já está em uso**
   ```
   Error: listen EADDRINUSE: address already in use 0.0.0.0:8080
   ```
   **Solução**: Verifique se outro processo está usando a porta 8080 (WildFly, outro Tomcat, etc.)
   ```powershell
   # Encontrar processos usando a porta 8080
   netstat -ano | findstr :8080
   
   # Encerrar o processo por ID (substitua {PID} pelo ID do processo)
   taskkill /F /PID {PID}
   ```

2. **Tomcat não consegue iniciar a aplicação**
   - Verifique o log em `meu-projeto-java/logs/application.log`
   - Verifique se existe o diretório webapp: `meu-projeto-java/src/main/webapp`
   - Verifique se as dependências estão corretas: `mvn dependency:tree`

3. **Classe WebServer não encontrada**
   - Execute o script `.\scr\tomcat\iniciar-tomcat.ps1` que compila e inicia automaticamente
   - Verifique se `WebServer.java` existe em `meu-projeto-java/src/main/java/com/exemplo/config/`

### Problemas com Servidor WildFly

1. **Erro ao iniciar WildFly**
   ```
   WFLYSRV0055: Caught exception during boot: org.jboss.as.controller.persistence.ConfigurationPersistenceException
   ```
   **Solução**: Restaurar arquivo de configuração do backup
   ```powershell
   # Restaurar o backup da configuração
   Copy-Item -Path "C:\dev\workspace\wildfly\standalone\configuration\standalone.xml.bak" -Destination "C:\dev\workspace\wildfly\standalone\configuration\standalone.xml" -Force
   ```

2. **Problemas com implantação no WildFly**
   ```
   WFLYUT0105: Host and context path are occupied
   ```
   **Solução**: Limpar diretórios de implantação e cache do WildFly
   ```powershell
   # Limpar deployments e cache
   Remove-Item -Path "C:\dev\workspace\wildfly\standalone\deployments\*" -Force
   Remove-Item -Path "C:\dev\workspace\wildfly\standalone\data\content\*" -Recurse -Force
   ```

3. **Configuração de datasource do WildFly inválida**
   ```
   IJ010061: Unexpected element: user
   ```
   **Solução**: Reconfigurar o datasource PostgreSQL para WildFly
   ```powershell
   # Reconfigurar datasource
   .\scr\wildfly\configurar-wildfly-bd.ps1
   ```

4. **WildFly não aparece no navegador**
   - Verifique se o processo está em execução: `Get-Process -Name java`
   - Verifique os logs: `Get-Content -Path "C:\dev\workspace\wildfly\standalone\log\server.log" -Tail 50`
   - Reinicie o servidor: `& "C:\dev\workspace\wildfly\bin\standalone.bat"`

### Problemas JSTL com Jakarta EE
Se encontrar erros como "Unable to load tag handler class org.apache.taglibs.standard.tag.rt.core.IfTag", verifique:
- Os URIs nos arquivos JSP devem usar `jakarta.tags.core` em vez de `http://java.sun.com/jsp/jstl/core`
- As dependências no pom.xml devem incluir `jakarta.servlet.jsp.jstl-api` e `jakarta.servlet.jsp.jstl`

```xml
<!-- JSTL para JSP (Jakarta EE) -->
<dependency>
    <groupId>jakarta.servlet.jsp.jstl</groupId>
    <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>org.glassfish.web</groupId>
    <artifactId>jakarta.servlet.jsp.jstl</artifactId>
    <version>3.0.1</version>
</dependency>
```

### Problemas de Conexão com Banco de Dados
Para solucionar problemas de conexão com PostgreSQL:
1. Verifique se o contêiner Docker está rodando: `docker ps`
2. Verifique a configuração HikariCP no JPAUtil.java
3. Confirme as credenciais corretas em persistence.xml
4. Execute: `.\scr\atualizar-banco-dados.ps1` para corrigir a estrutura do banco

### Problemas de Autenticação
Se o login não estiver funcionando:
1. Verifique se a coluna 'perfil' existe na tabela 'usuarios'
2. Confirme que as senhas estão armazenadas no formato correto
3. Execute: `.\scr\atualizar-senhas-db.ps1` para atualizar as senhas

## 📋 Estrutura do Banco de Dados (Resumo)

### Tabela de Usuários
```sql
CREATE TABLE usuarios (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    senha VARCHAR(255) NOT NULL,
    ativo BOOLEAN DEFAULT true,
    perfil VARCHAR(20) DEFAULT 'USUARIO' CHECK (perfil IN ('ADMIN', 'USUARIO')),
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Dados de Exemplo (Legado Simplificado)
O script atual usa hash BCrypt; abaixo apenas exemplo didático (NÃO condiz com o ambiente real pois lá as senhas estão cifradas):
```sql
-- Exemplo ilustrativo (não executado no init atual)
INSERT INTO usuarios (nome, email, senha, perfil) VALUES ('Admin Teste', 'admin@meuapp.com', 'Admin@123', 'ADMIN');
```

## 🏗️ Estrutura do Projeto (Simplificada)

```
workspace/
├── menu.ps1              # Menu principal da aplicação
├── README.md             # Documentação do projeto
├── docker-compose.yml    # Configuração do Docker
├── scr/                  # Scripts utilitários
│   ├── README.md         # Documentação dos scripts
│   ├── tomcat/           # Scripts para Tomcat
│   │   ├── deploy-tomcat.ps1          # Implantação no Tomcat
│   │   ├── iniciar-tomcat.ps1         # Iniciar Tomcat
│   │   └── testar-aplicacao-completa.ps1  # Testes
│   ├── wildfly/          # Scripts para WildFly
│   │   ├── adaptar-para-wildfly.ps1   # Adaptar para WildFly
│   │   ├── configurar-wildfly-bd.ps1  # Configurar BD
│   │   └── deploy-wildfly.ps1         # Implantar no WildFly
│   └── ...               # Outros scripts utilitários
├── docker/               # Configurações Docker
│   └── postgres/
│       └── init/         # Scripts de inicialização do banco
├── wildfly/              # Servidor WildFly (Produção)
│   ├── bin/              # Scripts de execução
│   │   ├── standalone.bat    # Iniciar WildFly (modo standalone)
│   │   └── jboss-cli.bat     # Cliente de linha de comando
│   ├── standalone/       # Configuração standalone
│   │   ├── configuration/    # Arquivos de configuração
│   │   │   └── standalone.xml    # Configuração principal
│   │   ├── deployments/      # Diretório de implantação
│   │   └── log/              # Logs do servidor
│   └── ...               # Outras pastas do WildFly
├── meu-projeto-java/     # Código-fonte da aplicação
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/exemplo/
│   │   │   │   ├── config/     # Configurações do sistema
│   │   │   │   │   └── WebServer.java.bak   # Configuração Tomcat (backup)
│   │   │   │   ├── dao/        # Objetos de acesso a dados
│   │   │   │   ├── model/      # Entidades JPA
│   │   │   │   └── servlet/    # Servlets para web
│   │   │   ├── resources/
│   │   │   │   └── META-INF/
│   │   │   │       └── persistence.xml # Configuração JPA
│   │   │   └── webapp/
│   │   │       ├── WEB-INF/
│   │   │       │   └── web.xml     # Configuração web
│   │   │       ├── index.jsp       # Página inicial
│   │   │       ├── login.jsp       # Página de login
│   │   │       └── dashboard.jsp   # Dashboard após login
│   │   └── test/
│   │       └── java/com/exemplo/   # Testes unitários
│   └── pom.xml           # Configuração Maven
├── tomcat.8080/          # Diretório de trabalho do Tomcat (Desenvolvimento)
│   └── work/            # Arquivos temporários do Tomcat
└── logs/                 # Logs da aplicação
    └── application.log   # Log principal da aplicação
```

## 🔄 Atualizações Recentes

1. **Reorganização de Scripts** - Estrutura mais organizada com scripts em diretórios específicos por função
2. **Integração com WildFly** - Suporte completo para implantação no servidor WildFly 37.0.1.Final
3. **Verificação Automática do PostgreSQL** - Validação do banco de dados no Docker antes da implantação
4. **Configuração Automatizada do WildFly** - Script para configurar datasource no WildFly
5. **Correção JSTL** - Atualização para compatibilidade com Jakarta EE
6. **Integração HikariCP** - Melhoria no gerenciamento de conexões
7. **Correção de Login** - Aprimoramento no sistema de autenticação
8. **Atualização de Banco** - Adição da coluna 'perfil' para usuários
9. **Menu Principal** - Menu centralizado com acesso a todas as funcionalidades
10. **Correção de Dependências Hibernate** - Adição das dependências hibernate-commons-annotations para WildFly
11. **Melhoria do Script Python** - Melhorias de diagnóstico e configuração de ambiente para Tomcat e WildFly
12. **Documentação Detalhada** - Adição de [documentação específica](scr/README.md) para os scripts utilitários

## � Documentação Adicional

Para facilitar o desenvolvimento e manutenção, este projeto inclui documentação detalhada:

- **[Documentação dos Scripts](scr/README.md)** - Descrição completa de todos os scripts utilitários, incluindo:
  - Finalidade e funcionalidade de cada script
  - Parâmetros e opções disponíveis
  - Exemplos de uso e casos comuns
  - Fluxos de trabalho recomendados

Esta documentação ajuda a entender como utilizar corretamente cada ferramenta disponível no projeto e como elas se integram para facilitar o desenvolvimento, testes e manutenção da aplicação.

## 🔄 Migração entre Servidores (Resumo)

Este projeto suporta tanto Tomcat quanto WildFly, mas a migração entre eles requer algumas considerações:

### Migrando de Tomcat para WildFly

1. **Adaptar dependências**:
   - Execute `.\scr\wildfly\adaptar-para-wildfly.ps1` para atualizar as dependências
   - Verifique se o namespace das importações foi atualizado de `javax.*` para `jakarta.*`

2. **Configurar datasource**:
   - Configuração do datasource no WildFly é diferente do Tomcat
   - Execute `.\scr\wildfly\configurar-wildfly-bd.ps1` para configurar

3. **Ajustar web.xml**:
   - Para WildFly, o `web.xml` precisa usar esquema Jakarta EE
   ```xml
   <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd"
          version="5.0">
   ```

4. **Implantação**:
   - Use `.\scr\wildfly\deploy-wildfly.ps1` para compilar e implantar no WildFly

### Migrando de WildFly para Tomcat

1. **Adaptar dependências**:
   - Ajuste o `pom.xml` para usar dependências compatíveis com Tomcat
   - Para versões mais antigas de Tomcat (< 10), volte para dependências `javax.*`

2. **Configurar datasource**:
   - Para Tomcat, use JNDI no arquivo `context.xml` ou configuração direta
   - Ajuste `persistence.xml` se necessário

3. **Ajustar web.xml**:
   - Para Tomcat < 10, volte para esquema JavaEE
   ```xml
   <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
          version="4.0">
   ```

4. **Implantação**:
   - Use `.\scr\tomcat\deploy-tomcat.ps1` para compilar e implantar no Tomcat

## 📚 Recursos Adicionais

- [Documentação do Tomcat](https://tomcat.apache.org/tomcat-10.0-doc/index.html)
- [Documentação do WildFly](https://docs.wildfly.org/27/)
- [Migração de JavaEE para JakartaEE](https://jakarta.ee/resources/JakartaEE-Datasheet-July2020_final.pdf)
- [Guia de migração Tomcat para WildFly](https://www.wildfly.org/news/2020/06/18/Jakarta-EE-Migration/)

## 📝 Próximos Passos

- [ ] Adicionar sistema de recuperação de senha
- [ ] Implementar controle de acesso baseado em perfis
- [ ] Adicionar páginas de gerenciamento de usuários
- [ ] Expandir funcionalidades do dashboard

---

**Desenvolvido com ❤️ usando Java Enterprise**
*Última atualização: 17 de Setembro 2025*