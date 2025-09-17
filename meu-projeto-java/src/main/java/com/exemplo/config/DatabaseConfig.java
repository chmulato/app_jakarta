package com.exemplo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Configuração de banco de dados com pool de conexões HikariCP
 */
public class DatabaseConfig {
    
    private static DatabaseConfig instance;
    private HikariDataSource dataSource;
    
    private DatabaseConfig() {
        initializeDataSource();
    }
    
    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }
    
    private void initializeDataSource() {
        HikariConfig config = new HikariConfig();
        
        // Configuração da conexão PostgreSQL
        String host = System.getProperty("DB_HOST", "localhost");
        String port = System.getProperty("DB_PORT", "5432");
        String database = System.getProperty("DB_NAME", "meu_app_db");
        String username = System.getProperty("DB_USER", "meu_app_user");
        String password = System.getProperty("DB_PASS", "meu_app_password");
        
        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Configurações do pool
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000); // 30 segundos
        config.setIdleTimeout(600000); // 10 minutos
        config.setMaxLifetime(1800000); // 30 minutos
        config.setLeakDetectionThreshold(60000); // 1 minuto
        
        // Configurações de validação
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        
        // Pool name para logs
        config.setPoolName("MeuAppHikariPool");
        
        try {
            this.dataSource = new HikariDataSource(config);
            System.out.println("✅ Pool de conexões PostgreSQL inicializado com sucesso!");
            System.out.println("📊 Configurações: " + 
                "Max=" + config.getMaximumPoolSize() + 
                ", Min=" + config.getMinimumIdle() + 
                ", URL=" + jdbcUrl);
        } catch (Exception e) {
            System.err.println("❌ Erro ao inicializar pool de conexões: " + e.getMessage());
            throw new RuntimeException("Falha na inicialização do banco de dados", e);
        }
    }
    
    /**
     * Obtém uma conexão do pool
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Pool de conexões não foi inicializado");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Obtém o DataSource
     */
    public DataSource getDataSource() {
        return dataSource;
    }
    
    /**
     * Testa a conectividade com o banco
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            System.err.println("❌ Teste de conexão falhou: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Obtém estatísticas do pool
     */
    public String getPoolStats() {
        if (dataSource == null) return "Pool não inicializado";
        
        return String.format(
            "Pool Stats - Ativas: %d, Idle: %d, Total: %d, Aguardando: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
    
    /**
     * Fecha o pool de conexões
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("🔒 Pool de conexões fechado");
        }
    }
    
    /**
     * Registra shutdown hook para fechar o pool
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Finalizando pool de conexões...");
            close();
        }));
    }
}