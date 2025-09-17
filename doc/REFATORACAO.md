# Refatoração dos Scripts de Deployment

## Linha do Tempo

| Data | Marco |
|------|-------|
| 16/09/2025 | Refatoração inicial de scripts PowerShell e perfis Maven |
| 17/09/2025 | Introdução do orquestrador Python `main.py` consolidando build/deploy/test |

## Estado Atual
O gerenciamento principal de build, deploy, diagnóstico e limpeza foi migrado para `main.py`, reduzindo dependência de múltiplos scripts PowerShell. Os scripts antigos permanecem como referência histórica ou uso pontual e poderão ser removidos/arquivados posteriormente.

### Motivação da Migração
| Problema Anterior | Solução Atual |
|-------------------|---------------|
| Duplicação de lógica entre vários `.ps1` | Centralização em funções Python reutilizáveis |
| Dificuldade em aplicar overrides dinâmicos | Suporte a args CLI (`--tomcat-dir`, `--wildfly-dir`, `--only-check`) |
| Logs fragmentados | Log unificado `log/maven_deploy.log` |
| Ajuste manual de portas | Tomcat porta configurada automaticamente durante deploy |
| Diagnóstico manual de falhas | Funções `diagnose_tomcat_issues()` e `diagnose_wildfly_issues()` |

## Mudanças Realizadas - 16 de setembro de 2025

### 1. Remoção de XML do Maven no PowerShell
- **Situação Anterior**: O script PowerShell continha tags XML para configurações Maven
- **Mudança**: Tags XML removidas e substituídas por perfis no pom.xml
- **Benefício**: Melhor separação de responsabilidades

### 2. Atualização para Tomcat 10.1.35
- **Situação Anterior**: Referências ao Tomcat 7
- **Mudança**: Atualizado para Tomcat 10.1.35
- **Benefício**: Suporte a Jakarta EE 10

### 3. Atualização para WildFly 37.0.1.Final
- **Situação Anterior**: Versão desatualizada do WildFly
- **Mudança**: Atualizado para WildFly 37.0.1.Final
- **Benefício**: Recursos mais recentes e correções de segurança

### 4. Centralização de Comandos Maven
- **Situação Anterior**: Comandos Maven espalhados pelo código
- **Mudança**: Função `Execute-MavenCommand` centralizada
- **Benefício**: Consistência, reuso e facilidade de manutenção

### 5. Script Maven-Commands.ps1
- **Situação Anterior**: Comandos Maven diretos, sem padronização
- **Mudança**: Novo script com atalhos para comandos comuns
- **Benefício**: Interface simplificada para desenvolvedores

### 6. Documentação Atualizada
- **Situação Anterior**: Documentação insuficiente sobre comandos Maven
- **Mudança**: Documentação completa em MAVEN-COMANDOS.md
- **Benefício**: Melhor experiência para desenvolvedores

### 7. Tratamento de Erros Melhorado
- **Situação Anterior**: Tratamento de erros inconsistente
- **Mudança**: Padrão consistente para tratamento e logging de erros
- **Benefício**: Melhor diagnóstico e resolução de problemas

## Arquivos Modificados
- `Start-App.ps1`: Refatorado para usar perfis Maven e centralizar comandos
- `Maven-Commands.ps1`: Novo script para execução simplificada de comandos Maven
- `doc\MAVEN-COMANDOS.md`: Documentação detalhada dos comandos Maven
- `Execute-RefactoredDeployment.ps1`: Script para testar a refatoração

## Como Usar (LEGADO / PowerShell)

### Método 1: Script Start-App.ps1 (Interface Completa)
```powershell
# Executar script principal
.\Start-App.ps1

# Opções relevantes:
# 5 - Iniciar Tomcat
# 6 - Iniciar WildFly
# 10 - Parar servidor atual
```

### Método 2: Maven-Commands.ps1 (Comandos Diretos)
```powershell
# Compilar e empacotar com perfil Tomcat
.\Maven-Commands.ps1 build tomcat

# Iniciar Tomcat
.\Maven-Commands.ps1 run tomcat

# Compilar e fazer deploy no WildFly
.\Maven-Commands.ps1 deploy wildfly
```

### Método 3: Verificar Refatoração
```powershell
# Executar testes da refatoração
.\Execute-RefactoredDeployment.ps1
```

## Perfis Maven (Atual)
Perfis disponíveis:
- `tomcat`
- `wildfly`
- `run`

Ver documentação em `doc/MAVEN-COMANDOS.md` para comandos revisados.

## Uso Recomendado (Atual)
```powershell
# Menu interativo
python .\main.py

# Apenas verificar ambiente
python .\main.py --only-check

# Override diretórios
python .\main.py --tomcat-dir C:\servers\tomcat10 --wildfly-dir C:\servers\wildfly37
```

## Próximos Passos Sugeridos
- Descontinuar scripts não utilizados após período de transição
- Adicionar testes automatizados para funções Python críticas
- Parametrizar portas por argumentos (`--tomcat-port`, `--wildfly-port`)