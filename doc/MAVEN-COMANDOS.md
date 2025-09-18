# Comandos Maven - Guia Atualizado

Este guia resume os comandos Maven efetivamente usados no fluxo automatizado (`main.py`) e manual para build, testes e deploy em Tomcat (porta 9090) e WildFly (porta 8080 / mgmt 9990).

## Índice

- [Perfis Maven](#perfis-maven)
- [Comandos Básicos](#comandos-básicos)
- [Comandos para Tomcat 10](#comandos-para-tomcat-10)
- [Comandos para WildFly](#comandos-para-wildfly)
- [Parâmetros Comuns](#parâmetros-comuns)
- [Solução de Problemas](#solução-de-problemas)

## Perfis Maven

| Perfil | Uso | Observação |
|--------|-----|------------|
| `tomcat` | Build + deploy WAR para Tomcat | Usa plugin `tomcat10-maven-plugin` |
| `wildfly` | Build para WildFly | Usa plugin `wildfly-maven-plugin` |
| `run` | Execução rápida (Tomcat incorporado/embedded) | Usado em desenvolvimento |

Verifique presença no `pom.xml` (o script `main.py` alerta se ausente).

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
# Build completo (sem testes)
mvn clean package -DskipTests

# Build completo (com testes)
mvn clean package

# Tomcat
mvn clean package -Ptomcat -DskipTests

# WildFly
mvn clean package -Pwildfly -DskipTests

# Execução rápida (embedded)
mvn -Prun
```

## Comandos para Tomcat 10 (Porta 9090)

### Build e Deploy para Tomcat 10

```bash
# Compilar e empacotar para Tomcat 10
mvn clean package -Ptomcat -DskipTests

# Iniciar (plugin Maven)
mvn tomcat10:run -Ptomcat -Dmaven.tomcat.port=9090

# Build + run
mvn clean package tomcat10:run -Ptomcat -DskipTests -Dmaven.tomcat.port=9090

# Run WAR empacotado
mvn tomcat10:run-war -Ptomcat -Dmaven.tomcat.port=9090

# Execução via perfil rápido (se configurado)
mvn -Prun

# Via script Python (recomendado)
python .\main.py  (menu → opção 2 ou 3)
```

### Opções Alternativas para Tomcat 10

```bash
# Modo de depuração detalhada
mvn -X clean package tomcat10:run-war -Ptomcat -DskipTests

# Compilação separada
mvn clean compile war:war tomcat10:run -Ptomcat -DskipTests
```

## Comandos para WildFly (Porta 8080 / Mgmt 9990)

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
# Alterar porta HTTP (exemplo)
mvn -Pwildfly -Djboss.http.port=8180 wildfly:deploy

# Alterar porta management
mvn -Pwildfly -Djboss.management.http.port=10090 wildfly:deploy

# Via script Python
python .\main.py  (menu → opção 4 ou 5)
```

## Parâmetros Comuns

| Parâmetro | Descrição | Exemplo |
|-----------|-----------|---------|
| `-DskipTests` | Pula a execução de testes unitários | `mvn package -DskipTests` |
| `-X` | Modo debug (saída detalhada) | `mvn -X clean package` |
| `-Dmaven.tomcat.port=9090` | Porta do Tomcat (plugin) | `mvn tomcat10:run -Dmaven.tomcat.port=9090` |
| `-Djboss.http.port=8080` | Porta HTTP WildFly | `mvn -Pwildfly -Djboss.http.port=8080 wildfly:deploy` |
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
# Limpeza completa
mvn clean

# Forçar atualização
mvn clean package -U -DskipTests

# Descrever plugin
mvn help:describe -Dplugin=wildfly
mvn help:describe -Dplugin=tomcat10

# Executar testes com relatório JaCoCo
mvn clean test verify

# Usar script Python para ambiente + build
python .\main.py --only-check
```