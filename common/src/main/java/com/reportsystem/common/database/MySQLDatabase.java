package com.reportsystem.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLDatabase implements Database {
    private final DatabaseConfig config;
    private HikariDataSource dataSource;
    private MySQLReportDAO reportDAO;
    private MySQLReplayDAO replayDAO;

    public MySQLDatabase(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public void connect() throws SQLException {
        // HikariCP bağlantı havuzu oluştur
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(hikariConfig);

        // DAO'ları oluştur (paylaşılan connection pool kullanarak)
        this.reportDAO = new MySQLReportDAO(dataSource);
        this.replayDAO = new MySQLReplayDAO(dataSource);
    }

    @Override
    public void close() throws SQLException {
        // Pool kapatma sadece burada yapılır, DAO'lar pool'u kapatmaz
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database is not connected!");
        }
        return dataSource.getConnection();
    }

    @Override
    public void createTables() throws SQLException {
        // DAO'lar kendi tablolarını oluşturur
        reportDAO.createTables();
    }

    public MySQLReportDAO getReportDAO() {
        return reportDAO;
    }

    public MySQLReplayDAO getReplayDAO() {
        return replayDAO;
    }
}