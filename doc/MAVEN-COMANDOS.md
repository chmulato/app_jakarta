# Comandos Maven para Configuração, Build e Deploy

Este documento serve como referência para os comandos Maven utilizados no projeto, detalhando as opções para configuração, build e deploy nos servidores Tomcat 10 e WildFly.

## Índice

- [Perfis Maven](#perfis-maven)
- [Comandos Básicos](#comandos-básicos)
- [Comandos para Tomcat 10](#comandos-para-tomcat-10)
- [Comandos para WildFly](#comandos-para-wildfly)
- [Parâmetros Comuns](#parâmetros-comuns)
- [Solução de Problemas](#solução-de-problemas)

## Perfis Maven

O projeto utiliza perfis Maven para separar as configurações específicas de cada servidor de aplicação:

- **tomcat**: Contém configurações, dependências e plugins específicos para o Apache Tomcat 10.1.35
- **wildfly**: Contém configurações, dependências e plugins específicos para o WildFly 37.0.1

## Comandos Básicos

### Verificação do Ambiente

```bash
# Verificar versão do Maven
mvn --version

# Verificar dependências do projeto
mvn dependency:tree

# Limpar diretório target
mvn clean

# Compilar apenas o código-fonte
mvn compile
```

### Build e Empacotamento

```bash
# Build completo (sem executar testes)
mvn clean package -DskipTests

# Build com testes
mvn clean package

# Build específico para Tomcat
mvn clean package -Ptomcat -DskipTests

# Build específico para WildFly
mvn clean package -Pwildfly -DskipTests
```

## Comandos para Tomcat 10

### Build e Deploy para Tomcat 10

```bash
# Compilar e empacotar para Tomcat 10
mvn clean package -Ptomcat -DskipTests

# Iniciar Tomcat 10 embarcado via plugin
mvn tomcat10:run -Ptomcat

# Compilar, empacotar e iniciar Tomcat 10 em um único comando
mvn clean package tomcat10:run -Ptomcat -DskipTests

# Iniciar Tomcat 10 com WAR já empacotado
mvn tomcat10:run-war -Ptomcat -Dmaven.tomcat.port=8080

# Executar a classe WebServer (para Tomcat embarcado)
mvn exec:java -Ptomcat -Dexec.mainClass="com.exemplo.server.tomcat.WebServer"
```

### Opções Alternativas para Tomcat 10

```bash
# Modo de depuração detalhada
mvn -X clean package tomcat10:run-war -Ptomcat -DskipTests

# Compilação separada
mvn clean compile war:war tomcat10:run -Ptomcat -DskipTests
```

## Comandos para WildFly

### Build e Deploy para WildFly

```bash
# Compilar e empacotar para WildFly
mvn clean package -Pwildfly -DskipTests

# Deploy no WildFly
mvn wildfly:deploy -Pwildfly

# Undeploy do WildFly
mvn wildfly:undeploy -Pwildfly

# Comando completo (build e deploy)
mvn clean package -Pwildfly wildfly:deploy -DskipTests
```

### Opções para Configuração do WildFly

```bash
# Configurar porta HTTP
mvn -Pwildfly -Djboss.http.port=9090 wildfly:deploy

# Configurar porta de administração
mvn -Pwildfly -Djboss.management.port=9990 wildfly:deploy
```

## Parâmetros Comuns

| Parâmetro | Descrição | Exemplo |
|-----------|-----------|---------|
| `-DskipTests` | Pula a execução de testes unitários | `mvn package -DskipTests` |
| `-X` | Modo debug (saída detalhada) | `mvn -X clean package` |
| `-Dmaven.tomcat.port=8080` | Define a porta do Tomcat | `mvn tomcat10:run -Dmaven.tomcat.port=8080` |
| `-Djboss.http.port=9090` | Define a porta HTTP do WildFly | `mvn -Djboss.http.port=9090` |
| `-Dexec.mainClass` | Define a classe principal para exec:java | `mvn exec:java -Dexec.mainClass="com.exemplo.Main"` |

## Solução de Problemas

### Problemas Comuns com Tomcat

| Problema | Solução |
|----------|---------|
| Erro de porta em uso | Use `-Dmaven.tomcat.port=8081` para mudar a porta |
| ClassNotFoundException | Verifique se o perfil `tomcat` está ativo (`-Ptomcat`) |
| Falhas de compilação | Use `-X` para depuração e verifique se todas as dependências estão presentes |

### Problemas Comuns com WildFly

| Problema | Solução |
|----------|---------|
| Connection refused | Verifique se o WildFly está em execução na porta configurada |
| Falha de autenticação | Configure as credenciais em `settings.xml` ou via linha de comando |
| Deployment timeout | Aumente o timeout com `-Dwildfly.timeout=120` |

### Comandos de Recuperação

```bash
# Limpar completamente o projeto
mvn clean

# Forçar atualização de dependências
mvn clean package -U -DskipTests

# Verificar problemas de plugin
mvn help:describe -Dplugin=wildfly
mvn help:describe -Dplugin=tomcat10
```