package com.reportsystem.velocity.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.reportsystem.common.database.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQLDatabase {

    private final HikariDataSource dataSource;

    public MySQLDatabase(DatabaseConfig config) throws SQLException {
        // Velocity'nin izole classloader'inda driver'i manuel yukle
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL driver bulunamadi!", e);
        }

        HikariConfig hikariConfig = new HikariConfig();

        // JDBC URL: sslMode config'ten (varsayilan DISABLED = eski useSSL=false davranisi)
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?sslMode=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                config.getHost(), config.getPort(), config.getDatabase(), config.getSslMode());

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Connection pool ayarlari (DatabaseConfig.withPool ile config'ten; sinirlara kirpilmis)
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
        hikariConfig.setLeakDetectionThreshold(60000);

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
        hikariConfig.addDataSourceProperty("characterEncoding", "utf8");
        hikariConfig.addDataSourceProperty("useUnicode", "true");

        try {
            this.dataSource = new HikariDataSource(hikariConfig);

            try (Connection conn = dataSource.getConnection()) {
                if (!conn.isValid(5)) {
                    throw new SQLException("Veritabani baglantisi gecerli degil!");
                }
            }
        } catch (SQLException e) {
            throw new SQLException("MySQL baglantisi kurulamadi: " + e.getMessage(), e);
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
