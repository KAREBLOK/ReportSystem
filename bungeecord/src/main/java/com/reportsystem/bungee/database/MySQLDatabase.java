package com.reportsystem.bungee.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.reportsystem.common.database.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQLDatabase {

    private final HikariDataSource dataSource;

    public MySQLDatabase(DatabaseConfig config) throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();

        // JDBC URL: sslMode config'ten (varsayilan DISABLED = eski useSSL=false davranisi)
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?sslMode=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                config.getHost(), config.getPort(), config.getDatabase(), config.getSslMode());

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Connection pool ayarları (DatabaseConfig.withPool ile config'ten; sınırlara kırpılmış)
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
        hikariConfig.setLeakDetectionThreshold(60000); // 1 dakika

        // MySQL specific settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
        // Encoding
        hikariConfig.addDataSourceProperty("characterEncoding", "utf8");
        hikariConfig.addDataSourceProperty("useUnicode", "true");

        try {
            this.dataSource = new HikariDataSource(hikariConfig);

            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                if (!conn.isValid(5)) {
                    throw new SQLException("Veritabanı bağlantısı geçerli değil!");
                }
            }
        } catch (SQLException e) {
            throw new SQLException("MySQL bağlantısı kurulamadı: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }
}