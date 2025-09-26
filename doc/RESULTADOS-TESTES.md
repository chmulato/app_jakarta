# Resultados dos Testes de Deploy (Atualizado)

## Histórico
| Data | Contexto |
|------|----------|
| 16/09/2025 | Testes pós-refatoração inicial (scripts PowerShell) |
| 17/09/2025 | Ajuste portas (Tomcat→9090 / WildFly→8080), adoção de `main.py` |
| 21/09/2025 | E2E (opção 12) validado em Tomcat e WildFly; login automatizado via fallback HTTP |

## Objetivo
Testar o deploy da aplicação Java nos servidores Tomcat e WildFly após refatoração da arquitetura.

## Infraestrutura
- **PostgreSQL**: ✅ Rodando no Docker (porta 5432) - Status: HEALTHY
- **Sistema**: Java 11, Maven, Windows + PowerShell

---

## Resultados por servidor (E2E atualizado)

### WildFly 37.0.1.Final (porta HTTP 8080 / mgmt 9990)

| Aspecto | Status | Detalhes |
|---------|--------|----------|
| **Compilação** | ✅ **SUCESSO** | Maven profile `-Pwildfly` |
| **Deploy** | ✅ **SUCESSO** | `wildfly:deploy` executado |
| **Servidor** | ✅ **RODANDO** | Porta 8080 (app) + 9990 (management) |
| **Banco de Dados** | ✅ **CONECTADO** | PostgreSQL integração OK |
| **Acesso Web** | ✅ **ACESSÍVEL** | http://localhost:8080/ |
| **Configuração** | ✅ **FLEXÍVEL** | Sem hardcode, via properties |

**Comando executado:**
```powershell
mvn clean package -Pwildfly -DskipTests
python .\main.py 12   # E2E inclui deploy e validação
```

**Resultado:** BUILD SUCCESS ✅

---

### Tomcat (porta 9090)

| Aspecto | Status | Detalhes |
|---------|--------|----------|
| **Compilação** | ✅ **SUCESSO** | Maven profile `-Ptomcat` |
| **Servidor** | ✅ **INICIADO** | Porta 9090 |
| **Banco de Dados** | ✅ **CONECTADO** | PostgreSQL integração OK |
| **JPA/Hibernate** | ✅ **SUCESSO** | Dependência adicionada |
| **Acesso Web** | ✅ **ACESSÍVEL** | Servidor completamente funcional |
| **Configuração** | ✅ **FLEXÍVEL** | Sem hardcode, via properties |

**Comando executado:**
```powershell
mvn clean package -Ptomcat -DskipTests
python .\main.py 12   # E2E inclui deploy e validação
```

**Correção aplicada:**
```xml
<!-- Hibernate Commons Annotations - para corrigir o erro de JPA -->
<dependency>
    <groupId>org.hibernate.common</groupId>
    <artifactId>hibernate-commons-annotations</artifactId>
    <version>6.0.6.Final</version>
</dependency>
```

---

## Observações de arquitetura (atual)
- Projeto renomeado para `caracore-hub`; pacote base `com.caracore.hub_town`.
- Contexto padrão `/caracore-hub` em ambos os servidores.
- JNDI distintos: Tomcat `java:comp/env/jdbc/PostgresDS`, WildFly `java:/jdbc/PostgresDS`.

---

## Status atual
| Servidor | Status | Observação |
|----------|--------|------------|
| WildFly  | ✅ Pronto | Deploy consistente via plugin ou script |
| Tomcat   | ✅ Pronto | Porta ajustada dinamicamente (9090) |

---

## Correções importantes (históricas)

Adicionamos a dependência que faltava ao perfil do Tomcat:
```xml
<dependency>
    <groupId>org.hibernate.common</groupId>
    <artifactId>hibernate-commons-annotations</artifactId>
    <version>6.0.6.Final</version>
</dependency>
```

**Resultado do teste histórico:**
```
Iniciando teste do Hibernate Commons Annotations...
✅ Classe ReflectionManager carregada com sucesso: org.hibernate.annotations.common.reflection.ReflectionManager
✅ Pacote: org.hibernate.annotations.common.reflection
✅ Versão: 6.0.6.Final
Teste concluído com sucesso! A dependência hibernate-commons-annotations está funcionando.
```

## Execução de testes (atual)
```powershell
mvn clean test verify
# Relatório cobertura: target/site/jacoco/index.html
```

Ou usando script (build sem testes + depois testes separados):
```powershell
python .\main.py --only-check
mvn test
```

## Testes Python (Fase 1)
```powershell
pytest tests/fase_01
```

### Resultado 26/09/2025
| Teste | Status | Observação |
|-------|--------|------------|
| Autenticação (login sucesso / erro) | ⏭️ Skip | Ambiente web não estava ativo durante a execução; testes aguardam `APP_TEST_BASE_URL`. |
| Fluxo de pedidos completo | ⏭️ Skip | Dependem da API rodando (`/api/pedidos`). |

> Os testes em Python agora fazem parte da suíte oficial da fase 1. Para executá-los, iniciar a aplicação via `python .\main.py 12` (ou subir Tomcat/WildFly manualmente) antes de rodar o `pytest`.

---

## Observações técnicas

- **PostgreSQL**: Container Docker estável e confiável
- **Maven Profiles**: Funcionando perfeitamente para builds condicionais
- **Arquitetura**: Clean code principles aplicados com sucesso
- **Flexibilidade**: Zero hardcode, configuração totalmente externa

**🏆 Conclusão:** E2E validado em 21/09/2025. WildFly e Tomcat funcionais, login automatizado confirmado (HTTP 302 → /caracore-hub/dashboard). Logs e CLI do WildFly podem ter timeouts intermitentes; não bloqueiam a validação.