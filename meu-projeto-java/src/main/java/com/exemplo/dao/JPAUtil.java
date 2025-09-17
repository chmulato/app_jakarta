package com.exemplo.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import com.exemplo.config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            // Verificar se banco está disponível
            logger.info("Verificando disponibilidade do banco de dados...");
            DatabaseConfig dbConfig = DatabaseConfig.getInstance();
            if (!dbConfig.testConnection()) {
                logger.error("Banco de dados não está acessível - JPA não será inicializado");
                return;
            }
            
            // Configurar propriedades para utilizar o DataSource diretamente
            logger.info("Configurando propriedades do JPA com DataSource...");
            Map<String, Object> properties = new HashMap<>();
            
            // Configuração para utilizar o DataSource e conexões diretas JDBC
            properties.put("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
            properties.put("hibernate.hikari.dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
            
            // Configuração do scanner de classes Hibernate/Jandex
            properties.put("hibernate.archive.scanner", "org.hibernate.boot.archive.scan.internal.DisabledScanner");
            
            // Obter configurações do PostgreSQL para consistência
            String jdbcHost = System.getProperty("DB_HOST", "localhost");
            String jdbcPort = System.getProperty("DB_PORT", "5432");
            String jdbcDatabase = System.getProperty("DB_NAME", "meu_app_db");
            String jdbcUsername = System.getProperty("DB_USER", "meu_app_user");
            String jdbcPassword = System.getProperty("DB_PASS", "meu_app_password");
            
            // Configurar as propriedades do PGSimpleDataSource
            properties.put("hibernate.hikari.dataSource.serverName", jdbcHost);
            properties.put("hibernate.hikari.dataSource.portNumber", jdbcPort);
            properties.put("hibernate.hikari.dataSource.databaseName", jdbcDatabase);
            properties.put("hibernate.hikari.dataSource.user", jdbcUsername);
            properties.put("hibernate.hikari.dataSource.password", jdbcPassword);
            
            // Configurações do pool HikariCP
            properties.put("hibernate.hikari.minimumIdle", "5");
            properties.put("hibernate.hikari.maximumPoolSize", "20");
            properties.put("hibernate.hikari.idleTimeout", "30000");
            
            // Configurar outras propriedades importantes
            properties.put("hibernate.hbm2ddl.auto", "update");
            
            // Desabilitar JSON format mapper que está causando problemas
            properties.put("hibernate.type.json.format_mapper", "none");
            
            // Inicializar EntityManagerFactory
            logger.info("Inicializando EntityManagerFactory para unidade de persistência: {}", PERSISTENCE_UNIT_NAME);
            entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties);
            
            initialized = true;
            logger.info("EntityManagerFactory JPA inicializado com sucesso!");
        } catch (Exception e) {
            logger.error("Erro ao inicializar EntityManagerFactory: {}", e.getMessage(), e);
            
            // Não lançar exceção para permitir que a aplicação continue sem JPA
            // throw new ExceptionInInitializerError(e);
        }
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