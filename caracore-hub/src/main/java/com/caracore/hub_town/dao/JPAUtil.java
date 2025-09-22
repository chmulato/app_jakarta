package com.caracore.hub_town.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import com.caracore.hub_town.config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

public class JPAUtil {
    private static final Logger logger = LogManager.getLogger(JPAUtil.class);
    private static final String PERSISTENCE_UNIT_NAME = "meuAppPU";
    private static EntityManagerFactory entityManagerFactory;
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) return;
        try {
            final boolean isWildFly = System.getProperty("jboss.server.base.dir") != null
                    || System.getProperty("jboss.server.name") != null;
            final boolean isTomcat = System.getProperty("catalina.base") != null
                    || System.getProperty("catalina.home") != null;

            Map<String, Object> properties = new HashMap<>();
            properties.put("hibernate.archive.scanner", "org.hibernate.boot.archive.scan.internal.DisabledScanner");
            properties.put("hibernate.hbm2ddl.auto", "update");
            properties.put("hibernate.type.json.format_mapper", "none");

            if (isWildFly) {
                entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties);
            } else if (isTomcat) {
                try {
                    InitialContext ic = new InitialContext();
                    Object looked = ic.lookup("java:comp/env/jdbc/PostgresDS");
                    if (looked instanceof DataSource) {
                        DataSource ds = (DataSource) looked;
                        try (Connection c = ds.getConnection()) {
                            logger.info("Conexão JNDI (Tomcat) OK: {}", c.getMetaData().getURL());
                        } catch (Exception ignored) {}
                        properties.put("jakarta.persistence.nonJtaDataSource", ds);
                        entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties);
                    }
                } catch (NamingException ne) {
                    logger.warn("Sem JNDI no Tomcat, usando standalone: {}", ne.getMessage());
                    entityManagerFactory = buildStandaloneEMF(properties);
                }
            } else {
                entityManagerFactory = buildStandaloneEMF(properties);
            }
            initialized = true;
        } catch (Exception e) {
            logger.error("Erro inicializando EMF: {}", e.getMessage(), e);
        }
    }

    private static EntityManagerFactory buildStandaloneEMF(Map<String, Object> baseProps) {
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        if (!dbConfig.testConnection()) {
            logger.error("Banco indisponível para modo standalone");
            return null;
        }
        Map<String, Object> properties = new HashMap<>(baseProps);
        properties.put("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        String jdbcHost = System.getProperty("DB_HOST", "localhost");
        String jdbcPort = System.getProperty("DB_PORT", "5432");
        String jdbcDatabase = System.getProperty("DB_NAME", "meu_app_db");
        String jdbcUsername = System.getProperty("DB_USER", "meu_app_user");
        String jdbcPassword = System.getProperty("DB_PASS", "meu_app_password");
        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", jdbcHost, jdbcPort, jdbcDatabase);
        properties.put("hibernate.hikari.jdbcUrl", jdbcUrl);
        properties.put("hibernate.hikari.username", jdbcUsername);
        properties.put("hibernate.hikari.password", jdbcPassword);
        properties.put("hibernate.hikari.driverClassName", "org.postgresql.Driver");
        properties.put("hibernate.hikari.minimumIdle", "1");
        properties.put("hibernate.hikari.maximumPoolSize", "5");
        properties.put("hibernate.hikari.idleTimeout", "30000");
        properties.put("hibernate.hikari.leakDetectionThreshold", "20000");
        return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, properties);
    }

    public static EntityManager getEntityManager() {
        if (!initialized) initialize();
        if (entityManagerFactory == null) {
            entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
            initialized = true;
        }
        return entityManagerFactory.createEntityManager();
    }

    public static <T> T executeInTransaction(TransactionCallback<T> callback) {
        EntityManager em = getEntityManager();
        try {
            em.getTransaction().begin();
            T result = callback.execute(em);
            em.getTransaction().commit();
            return result;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw new RuntimeException("Erro na transação: " + e.getMessage(), e);
        } finally {
            if (em.isOpen()) em.close();
        }
    }

    public static void executeInTransaction(TransactionAction action) {
        EntityManager em = getEntityManager();
        try {
            em.getTransaction().begin();
            action.execute(em);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw new RuntimeException("Erro na transação: " + e.getMessage(), e);
        } finally {
            if (em.isOpen()) em.close();
        }
    }

    @FunctionalInterface
    public interface TransactionCallback<T> { T execute(EntityManager em) throws Exception; }

    @FunctionalInterface
    public interface TransactionAction { void execute(EntityManager em) throws Exception; }
}