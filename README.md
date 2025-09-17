# 🚀 Projeto Java Web com Autenticação

Este projeto é uma aplicação web Java Enterprise com sistema de autenticação, interface responsiva e integração com banco de dados PostgreSQL. Suporta tanto o servidor Tomcat Embedded (desenvolvimento) quanto o WildFly (produção).

## � Tecnologias Utilizadas

### Backend
- **Java 11** - Linguagem de programação
- **Jakarta EE (Jakarta Servlet 6.0.0)** - Especificação enterprise
- **Maven** - Gerenciamento de dependências e build
- **Servidores**:
  - **Tomcat Embedded 10.1.26** - Servidor web embutido (desenvolvimento)
  - **WildFly 29.0.1.Final** - Servidor de aplicação completo (produção)

### Banco de Dados
- **PostgreSQL 15** - Banco de dados relacional principal (Docker)
- **HikariCP 5.1.0** - Pool de conexões de alta performance
- **Hibernate ORM 6.4.4.Final** - ORM para persistência de dados

### Frontend
- **JSP + JSTL (Jakarta EE)** - Java Server Pages para renderização dinâmica
- **Bootstrap 5.3** - Framework CSS responsivo moderno
- **Bootstrap Icons** - Ícones vetoriais

## 🔑 Funcionalidades Principais

- ✅ **Login Seguro** - Autenticação com email e senha
- ✅ **Controle de Sessão** - Gerenciamento de usuários logados
- ✅ **Dashboard Administrativo** - Interface para usuários autenticados
- ✅ **Layout Responsivo** - Funciona em dispositivos móveis e desktop

## 🚀 Como Executar

### Pré-requisitos
- Java 11+
- Maven
- Docker e Docker Compose

### Iniciar o PostgreSQL
```powershell
# Iniciar o PostgreSQL
docker-compose up -d postgres

# Verificar se a estrutura do banco está correta
.\scr\verificar-estrutura-bd.ps1

# Atualizar estrutura se necessário
.\scr\atualizar-banco-dados.ps1
```

### Executar a Aplicação
```powershell
# Opção 1: Menu principal com todas as opções
.\menu.ps1

# Opção 2: Usando Maven diretamente
cd meu-projeto-java
mvn -Prun
```

### Configuração e Execução com Tomcat (Desenvolvimento)

O Tomcat Embedded é usado para desenvolvimento rápido e testes locais. Este servidor é incorporado diretamente na aplicação.

```powershell
# Iniciar a aplicação com Tomcat Embedded
.\scr\tomcat\iniciar-tomcat.ps1

# Compilar e implantar manualmente no Tomcat Embedded
.\scr\tomcat\deploy-tomcat.ps1

# Executar testes completos com Tomcat
.\scr\tomcat\testar-aplicacao-completa.ps1
```

**Características do Tomcat:**
- Servidor leve e rápido para iniciar
- Incorporado diretamente na aplicação (não precisa de instalação separada)
- Ideal para desenvolvimento local e depuração
- Acesso à aplicação: http://localhost:8080/meu-projeto-java/

### Configuração e Execução com WildFly (Produção)

O WildFly é um servidor de aplicação completo recomendado para ambientes de produção, oferecendo recursos avançados como gerenciamento de cluster, monitoramento e administração via console web.

```powershell
# Passo 1: Adaptar o projeto para WildFly
.\scr\wildfly\adaptar-para-wildfly.ps1

# Passo 2: Configurar conexão com PostgreSQL no WildFly
.\scr\wildfly\configurar-wildfly-bd.ps1

# Passo 3: Compilar e implantar no WildFly
.\scr\wildfly\deploy-wildfly.ps1
```

**Características do WildFly:**
- Servidor de aplicação completo para produção
- Suporte a clustering e alta disponibilidade
- Console de administração web: http://localhost:9990/
- Acesso à aplicação: http://localhost:8080/meu-projeto-java
- Gerenciamento via linha de comando: `C:\dev\workspace\wildfly\bin\jboss-cli.bat`

**Estrutura de Implantação do WildFly:**
- Diretório de implantação: `C:\dev\workspace\wildfly\standalone\deployments\`
- Arquivo de configuração: `C:\dev\workspace\wildfly\standalone\configuration\standalone.xml`
- Logs: `C:\dev\workspace\wildfly\standalone\log\server.log`

### Comparação entre Tomcat e WildFly

| Característica | Tomcat Embedded | WildFly |
|----------------|-----------------|---------|
| **Tipo** | Servidor web + servlet container | Servidor de aplicação completo (Java EE/Jakarta EE) |
| **Uso ideal** | Desenvolvimento, testes, aplicações simples | Produção, aplicações complexas, alta disponibilidade |
| **Tamanho** | Leve (~15-20MB) | Completo (~200MB+) |
| **Inicialização** | Rápida (segundos) | Mais lenta (até minutos na primeira vez) |
| **Recursos** | Básicos (servlets, JSP, WebSockets) | Completos (EJB, JPA, JAX-RS, JMS, etc.) |
| **Configuração** | Simples, poucos arquivos | Mais complexa, múltiplos arquivos XML |
| **Administração** | Básica, via arquivos de configuração | Console web, CLI, APIs de gerenciamento |
| **Escalabilidade** | Limitada | Alta (clustering, load balancing) |
| **Monitoramento** | Básico | Avançado (métricas, health checks) |
| **Segurança** | Básica | Avançada (RBAC, SSO, LDAP) |

**Quando usar Tomcat:**
- Durante o desenvolvimento ativo
- Para aplicações web simples
- Quando recursos são limitados (menos RAM/CPU)
- Para inicialização rápida durante ciclos de desenvolvimento

**Quando usar WildFly:**
- Em ambientes de produção
- Para aplicações empresariais complexas
- Quando necessitar recursos Java EE/Jakarta EE completos
- Para aplicações que precisam de alta disponibilidade

### Acessar a Aplicação
- **URL**: http://localhost:8080/meu-projeto-java/
- **Login**: http://localhost:8080/meu-projeto-java/login
- **Credenciais**: admin@exemplo.com / admin123

## 🧰 Scripts Utilitários

O projeto inclui vários scripts PowerShell para facilitar o desenvolvimento:

### Scripts Principais
- **menu.ps1** - Menu principal com todas as opções de gerenciamento
- **run.ps1** - Inicia a aplicação com Tomcat para desenvolvimento

### Scripts Utilitários (pasta scr)
A pasta `scr` contém vários scripts utilitários organizados por função:

- **scr/tomcat/** - Scripts específicos para o servidor Tomcat embarcado
  - **iniciar-tomcat.ps1** - Inicia a aplicação com Tomcat embarcado
  - **deploy-tomcat.ps1** - Compila e implanta no Tomcat
  - **testar-aplicacao-completa.ps1** - Executa testes completos no Tomcat

- **scr/wildfly/** - Scripts específicos para o servidor WildFly
  - **adaptar-para-wildfly.ps1** - Adapta o projeto para WildFly
  - **configurar-wildfly-bd.ps1** - Configura o WildFly para PostgreSQL
  - **deploy-wildfly.ps1** - Compila e implanta no WildFly

- **scr/** (raiz) - Scripts utilitários gerais para diagnóstico e manutenção

📚 **[Consulte a documentação completa dos scripts aqui](scr/README.md)**

## 🔍 Solução de Problemas Comuns

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

## 📋 Estrutura do Banco de Dados

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

### Dados de Exemplo
```sql
-- Credenciais: admin@exemplo.com / admin123
INSERT INTO usuarios (nome, email, senha, perfil) 
VALUES ('Admin Teste', 'admin@exemplo.com', 'admin123', 'ADMIN');
```

## 🏗️ Estrutura do Projeto

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

## 🔄 Migração entre Servidores

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

## �📝 Próximos Passos

- [ ] Adicionar sistema de recuperação de senha
- [ ] Implementar controle de acesso baseado em perfis
- [ ] Adicionar páginas de gerenciamento de usuários
- [ ] Expandir funcionalidades do dashboard

---

**Desenvolvido com ❤️ usando Java Enterprise**
*Última atualização: Junho 2023*