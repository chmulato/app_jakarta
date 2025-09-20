package com.exemplo.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import com.exemplo.config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Gerenciador JPA para EntityManager e transações
 */
public class JPAUtil {
    
    private static final Logger logger = LogManager.getLogger(JPAUtil.class);
    private static final String PERSISTENCE_UNIT_NAME = "meuAppPU";
    private static EntityManagerFactory entityManagerFactory;
    private static boolean initialized = false;
    
    /**
     * Inicializa o EntityManagerFactory usando o DataSource configurado
     */
    public static synchronized void initialize() {
        if (initialized) {
            logger.debug("EntityManagerFactory já inicializado - ignorando chamada");
            return;
        }
        
        try {
            // Detectar o ambiente (WildFly vs Tomcat vs standalone)
            final boolean isWildFly = System.getProperty("jboss.server.base.dir") != null
                    || System.getProperty("jboss.server.name") != null;
            final boolean isTomcat = System.getProperty("catalina.base") != null
                    || System.getProperty("catalina.home") != null;

            logger.info("Inicializando JPA (PU: {}) - Ambiente detectado: {}",
                    PERSISTENCE_UNIT_NAME,
                    isWildFly ? "WildFly" : (isTomcat ? "Tomcat" : "Standalone"));

            Map<String, Object> properties = new HashMap<>();
            // Configuração comum
            properties.put("hibernate.archive.scanner", "org.hibernate.boot.archive.scan.internal.DisabledScanner");
            properties.put("hibernate.hbm2ddl.auto", "update");
            properties.put("hibernate.type.json.format_mapper", "none");

            if (isWildFly) {
                // WildFly: usar o datasource definido no persistence.xml (java:/jdbc/PostgresDS)
                // Não configurar Hikari nem datasource programático – o container gerencia o pool
                logger.info("WildFly detectado: usando datasource do persistence.xml (java:/jdbc/PostgresDS)");
                entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties);
            } else if (isTomcat) {
                // Tomcat: realizar lookup JNDI em java:comp/env/jdbc/PostgresDS
                logger.info("Tomcat detectado: procurando DataSource via JNDI em java:comp/env/jdbc/PostgresDS");
                DataSource ds = null;
                try {
                    InitialContext ic = new InitialContext();
                    Object looked = ic.lookup("java:comp/env/jdbc/PostgresDS");
                    if (looked instanceof DataSource) {
                        ds = (DataSource) looked;
                    } else {
                        throw new NamingException("Objeto JNDI não é DataSource: " + (looked != null ? looked.getClass() : "null"));
                    }
                } catch (NamingException ne) {
                    logger.error("Falha ao localizar DataSource no JNDI do Tomcat: {}", ne.getMessage(), ne);
                    // fallback para configuração standalone abaixo
                }

                if (ds != null) {
                    // Opcional: testar conexão rapidamente para diagnóstico
                    try (Connection c = ds.getConnection()) {
                        logger.info("Conexão JNDI (Tomcat) bem-sucedida: {}", c.getMetaData().getURL());
                    } catch (Exception e) {
                        logger.warn("Não foi possível testar conexão do DataSource JNDI: {}", e.getMessage());
                    }
                    properties.put("jakarta.persistence.nonJtaDataSource", ds);
                    entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties);
                } else {
                    logger.warn("Prosseguindo com configuração standalone (Hikari) no Tomcat por ausência de JNDI.");
                    entityManagerFactory = buildStandaloneEMF(properties);
                }
            } else {
                // Standalone (por exemplo, testes locais sem container)
                entityManagerFactory = buildStandaloneEMF(properties);
            }
            
            initialized = true;
            logger.info("EntityManagerFactory JPA inicializado com sucesso!");
        } catch (Exception e) {
            logger.error("Erro ao inicializar EntityManagerFactory: {}", e.getMessage(), e);
            
            // Não lançar exceção para permitir que a aplicação continue sem JPA
            // throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Constrói um EntityManagerFactory usando configurações standalone (Hikari via propriedades do Hibernate).
     */
    private static EntityManagerFactory buildStandaloneEMF(Map<String, Object> baseProps) {
        // Verificar disponibilidade do banco usando DatabaseConfig
        logger.info("Inicializando EMF em modo standalone (Hikari + JDBC URL)...");
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        if (!dbConfig.testConnection()) {
            logger.error("Banco de dados não está acessível - JPA não será inicializado em modo standalone");
            return null;
        }

        Map<String, Object> properties = new HashMap<>(baseProps);
        // Usar o provider HikariCP
        properties.put("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");

        // Obter configurações do PostgreSQL
        String jdbcHost = System.getProperty("DB_HOST", "localhost");
        String jdbcPort = System.getProperty("DB_PORT", "5432");
        String jdbcDatabase = System.getProperty("DB_NAME", "meu_app_db");
        String jdbcUsername = System.getProperty("DB_USER", "meu_app_user");
        String jdbcPassword = System.getProperty("DB_PASS", "meu_app_password");

        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", jdbcHost, jdbcPort, jdbcDatabase);

        // Configurar Hikari via JDBC URL (sem dataSourceClassName)
        properties.put("hibernate.hikari.jdbcUrl", jdbcUrl);
        properties.put("hibernate.hikari.username", jdbcUsername);
        properties.put("hibernate.hikari.password", jdbcPassword);
        properties.put("hibernate.hikari.driverClassName", "org.postgresql.Driver");

        // Pool reduzido
        properties.put("hibernate.hikari.minimumIdle", "1");
        properties.put("hibernate.hikari.maximumPoolSize", "5");
        properties.put("hibernate.hikari.idleTimeout", "30000");
        properties.put("hibernate.hikari.leakDetectionThreshold", "20000");

        logger.info("Inicializando EntityManagerFactory (standalone) para unidade de persistência: {}", PERSISTENCE_UNIT_NAME);
        return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties);
    }
    
    /**
     * Obtém um EntityManager
     */
    public static EntityManager getEntityManager() {
        if (!initialized) {
            logger.debug("EntityManagerFactory não estava inicializado - inicializando agora");
            initialize();
        }
        
        if (entityManagerFactory == null) {
            logger.error("EntityManagerFactory não foi inicializado corretamente");
            // Em vez de lançar exceção, vamos tentar uma segunda inicialização com configurações padrão
            try {
                logger.info("Tentando inicializar EntityManagerFactory com configuração padrão...");
                entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
                initialized = true;
                logger.info("EntityManagerFactory JPA inicializado com configuração padrão!");
            } catch (Exception e) {
                logger.error("Falha na segunda tentativa de inicialização: {}", e.getMessage(), e);
                throw new IllegalStateException("EntityManagerFactory não pôde ser inicializado", e);
            }
        }
        
        logger.debug("Criando novo EntityManager");
        return entityManagerFactory.createEntityManager();
    }
    
    /**
     * Obtém o EntityManagerFactory
     */
    public static EntityManagerFactory getEntityManagerFactory() {
        if (!initialized) {
            logger.debug("EntityManagerFactory não estava inicializado - inicializando agora");
            initialize();
        }
        
        return entityManagerFactory;
    }
    
    /**
     * Executa operação em transação
     */
    public static <T> T executeInTransaction(TransactionCallback<T> callback) {
        EntityManager em = getEntityManager();
        logger.debug("Iniciando transação");
        try {
            em.getTransaction().begin();
            T result = callback.execute(em);
            em.getTransaction().commit();
            logger.debug("Transação concluída com sucesso");
            return result;
        } catch (Exception e) {
            logger.error("Erro na transação: {}", e.getMessage(), e);
            if (em.getTransaction().isActive()) {
                logger.debug("Realizando rollback da transação");
                em.getTransaction().rollback();
            }
            throw new RuntimeException("Erro na transação: " + e.getMessage(), e);
        } finally {
            if (em.isOpen()) {
                logger.debug("Fechando EntityManager");
                em.close();
            }
        }
    }
    
    /**
     * Executa operação em transação sem retorno
     */
    public static void executeInTransaction(TransactionAction action) {
        executeInTransaction(em -> {
            action.execute(em);
            return null;
        });
    }
    
    /**
     * Fecha o EntityManagerFactory
     */
    public static void close() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            logger.info("Fechando EntityManagerFactory");
            entityManagerFactory.close();
            initialized = false;
            logger.info("EntityManagerFactory fechado com sucesso");
        }
    }
    
    /**
     * Registra shutdown hook para fechar o EntityManagerFactory
     */
    public static void registerShutdownHook() {
        logger.info("Registrando shutdown hook para EntityManagerFactory");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Executando shutdown hook - finalizando EntityManagerFactory...");
            close();
        }));
    }
    
    /**
     * Interface para operações com transação que retornam valor
     */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(EntityManager em) throws Exception;
    }
    
    /**
     * Interface para operações com transação sem retorno
     */
    @FunctionalInterface
    public interface TransactionAction {
        void execute(EntityManager em) throws Exception;
    }
}