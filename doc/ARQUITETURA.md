# Arquitetura de Servidores - Projeto Java

## 📋 Visão Geral

O projeto foi refatorado para suportar múltiplos servidores de aplicação (Tomcat e WildFly) de forma limpa e organizada, sem dependências hardcoded ou acoplamento desnecessário.

## 🏗️ Estrutura de Arquitetura

### 📁 Organização de Pacotes

```
src/main/java/com/exemplo/
├── server/                          # Pacote base para servidores
│   ├── WebServerInterface.java      # Interface comum para servidores
│   ├── AbstractWebServer.java       # Classe base com funcionalidades comuns
│   ├── tomcat/                      # Implementações específicas do Tomcat
│   │   └── WebServer.java           # Servidor Tomcat embedded (só no perfil tomcat)
│   └── wildfly/                     # Implementações específicas do WildFly
│       └── WildFlyServer.java       # Controle programático WildFly (opcional)
├── servlet/                         # Servlets da aplicação (comum)
├── dao/                            # Data Access Objects (comum)
├── config/                         # Configurações (comum)
└── model/                          # Modelos de dados (comum)
```

### 🎯 Principios Arquiteturais

#### ✅ **Separação de Responsabilidades**
- **Classes Comuns**: Não contêm dependências específicas de servidor
- **Classes Específicas**: Isoladas em pacotes por servidor (`server/tomcat/`)
- **Configurações**: Centralizadas no `pom.xml` via propriedades Maven

#### ✅ **Compilação Condicional**
- **Perfil WildFly**: Exclui pacote `**/server/tomcat/**` da compilação
- **Perfil Tomcat**: Inclui todas as classes necessárias
- **Resultado**: WildFly compila 12 arquivos, Tomcat compila 13 arquivos

#### ✅ **Configuração via Propriedades**
- **Portas**: Definidas no `pom.xml`, não hardcoded no código
- **Override**: Suporte a propriedades de sistema (`-Dserver.port=8081`)
- **Flexibilidade**: Fácil personalização por ambiente

## ⚙️ Configurações de Porta

### 📊 Portas Configuradas

```
| Servidor    | Aplicação   | Administração | Configuração                 |
|-------------|-------------|---------------|------------------------------|
| **Tomcat**  | 8080        | N/A           | `pom.xml` → `tomcat.port`    |
| **WildFly** | 9090        | 9990          | `standalone.xml` + `pom.xml` |
```

### 🔧 Localização das Configurações

#### Maven Properties (`pom.xml`)
```xml
<properties>
    <tomcat.port>8080</tomcat.port>
    <wildfly.http.port>9090</wildfly.http.port>
    <wildfly.management.port>9990</wildfly.management.port>
</properties>
```

#### Java Code (AbstractWebServer.java)
```java
protected int getConfiguredPort(int defaultPort) {
    String portProperty = System.getProperty("server.port", String.valueOf(defaultPort));
    return Integer.parseInt(portProperty);
}
```

#### WildFly Configuration (`standalone.xml`)
```xml
<socket-binding name="http" port="${jboss.http.port:9090}"/>
<socket-binding name="management-http" port="${jboss.management.http.port:9990}"/>
```

## 🚀 Execução

### 🐱 Tomcat (Embedded)
```bash
# Porta padrão (8080)
mvn clean compile -Ptomcat exec:java

# Porta customizada
mvn clean compile -Ptomcat exec:java -Dserver.port=8081

# Via script
.\iniciar-tomcat-perfil.ps1
```

### 🦬 WildFly (Standalone)
```bash
# Deploy padrão
mvn clean package -Pwildfly wildfly:deploy

# Via script
.\iniciar-wildfly-perfil.ps1
```

## 🌐 URLs de Acesso

| Servidor | URL da Aplicação | URL de Administração |
|----------|------------------|---------------------|
| **Tomcat** | http://localhost:8080/ | N/A |
| **WildFly** | http://localhost:9090/meu-projeto-java | http://localhost:9990/console |

## ✅ Vantagens da Nova Arquitetura

### 🎯 **Flexibilidade**
- ✓ Suporte a múltiplos servidores sem conflito
- ✓ Configuração centralizada e personalizável
- ✓ Compilação condicional por perfil

### 🔧 **Manutenibilidade**
- ✓ Código específico isolado por servidor
- ✓ Interface comum para funcionalidades básicas
- ✓ Remoção de dependências hardcoded

### 🏗️ **Extensibilidade**
- ✓ Fácil adição de novos servidores (ex: Jetty)
- ✓ Padrão Template Method para implementações
- ✓ Configuração via propriedades Maven

### 🛡️ **Robustez**
- ✓ Tratamento de erros centralizado
- ✓ Logging consistente via Log4j2
- ✓ Validação de configurações

## 📝 Scripts Disponíveis

| Script | Função | Servidor |
|--------|--------|----------|
| `iniciar-tomcat-perfil.ps1` | Deploy automático Tomcat | Tomcat |
| `iniciar-wildfly-perfil.ps1` | Deploy automático WildFly | WildFly |
| `configuracoes-portas.ps1` | Documentação das configurações | Ambos |
| `testar-portas.ps1` | Teste de disponibilidade de portas | Ambos |

## 🔄 Exemplos de Uso

### Desenvolvimento Local
```bash
# PostgreSQL
docker-compose up -d postgres

# Tomcat (desenvolvimento rápido)
mvn clean compile -Ptomcat exec:java

# WildFly (ambiente similar à produção)
mvn clean package -Pwildfly wildfly:deploy
```

### 🔧 Personalização de Ambiente
```bash
# Tomcat em porta diferente
mvn clean compile -Ptomcat exec:java -Dserver.port=8081

# WildFly com configuração customizada
mvn clean package -Pwildfly wildfly:deploy

# WildFly programático com caminho customizado
mvn clean compile -Pwildfly-embedded exec:java \
  -Dwildfly.home=/meu/wildfly \
  -Dserver.port=9091 \
  -Dwildfly.management.port=9991
```

## 🎉 Resultado

A arquitetura agora é:
- ✅ **Limpa**: Sem dependências desnecessárias entre servidores
- ✅ **Flexível**: Configuração via propriedades, não hardcoded
- ✅ **Escalável**: Fácil adição de novos servidores
- ✅ **Robusta**: Compilação condicional por perfil Maven