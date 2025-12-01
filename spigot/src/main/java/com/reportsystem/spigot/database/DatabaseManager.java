package com.reportsystem.spigot.database;

import com.reportsystem.common.database.*;
import com.reportsystem.spigot.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Veritabanı yönetimi için ana sınıf
 * MySQL ve SQLite desteği sağlar
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    private Database database;
    private ReportDAO reportDAO;
    private ReplayDAO replayDAO;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Veritabanı bağlantısını başlatır
     */
    public void initialize() throws SQLException {
        String dbType = configManager.getDatabaseType();

        if (dbType.equalsIgnoreCase("mysql")) {
            initializeMySQL();
        } else {
            initializeSQLite();
        }

        // Tabloları oluştur
        createTables();

        plugin.getLogger().info("Database initialized successfully (" + dbType + ")");
    }

    /**
     * MySQL bağlantısını başlatır
     */
    private void initializeMySQL() throws SQLException {
        // HikariCP konfigürasyonu
        HikariConfig config = new HikariConfig();

        // JDBC URL
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&autoReconnect=true",
                configManager.getMySQLHost(),
                configManager.getMySQLPort(),
                configManager.getMySQLDatabase()
        );

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(configManager.getMySQLUsername());
        config.setPassword(configManager.getMySQLPassword());

        // Connection pool ayarları
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000); // 30 saniye
        config.setIdleTimeout(600000); // 10 dakika
        config.setMaxLifetime(1800000); // 30 dakika

        // Performans ayarları
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        // Pool adı
        config.setPoolName("ReportSystem-MySQL-Pool");

        // DataSource oluştur
        this.dataSource = new HikariDataSource(config);

        // DÜZELTİLDİ: Database wrapper oluştur
        DatabaseConfig dbConfig = new DatabaseConfig(
                configManager.getMySQLHost(),
                configManager.getMySQLPort(),
                configManager.getMySQLDatabase(),
                configManager.getMySQLUsername(),
                configManager.getMySQLPassword()
        );
        this.database = new MySQLDatabase(dbConfig);


        // DÜZELTİLDİ: DAO'ları oluştur - Düzeltilmiş constructor
        this.reportDAO = new MySQLReportDAO(
                configManager.getMySQLHost(),
                configManager.getMySQLPort(),
                configManager.getMySQLDatabase(),
                configManager.getMySQLUsername(),
                configManager.getMySQLPassword()
        );

        this.replayDAO = new MySQLReplayDAO(
                configManager.getMySQLHost(),
                configManager.getMySQLPort(),
                configManager.getMySQLDatabase(),
                configManager.getMySQLUsername(),
                configManager.getMySQLPassword()
        );

        plugin.getLogger().info("MySQL connection pool created successfully");
    }

    /**
     * SQLite bağlantısını başlatır
     */
    private void initializeSQLite() throws SQLException {
        // Database dosyası (direkt plugin klasöründe)
        File dbFile = new File(plugin.getDataFolder(), "reports.db");
        String dbPath = dbFile.getAbsolutePath();

        // DÜZELTİLDİ: Database config
        DatabaseConfig dbConfig = new DatabaseConfig(plugin.getDataFolder());

        // DÜZELTİLDİ: Database oluştur
        this.database = new SQLiteDatabase(new File(dbPath));
        this.database.connect();

        // DAO'ları oluştur
        SQLiteDatabase sqliteDb = (SQLiteDatabase) database;
        this.reportDAO = sqliteDb.getReportDAO();
        this.replayDAO = sqliteDb.getReplayDAO();

        plugin.getLogger().info("SQLite database initialized at: " + dbPath);
    }

    /**
     * Veritabanı tablolarını oluşturur
     */
    private void createTables() {
        try {
            database.createTables();
            plugin.getLogger().info("Database tables created/verified successfully");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Test bağlantısı yapar
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database connection test failed", e);
            return false;
        }
    }

    /**
     * Bağlantı döndürür
     */
    public Connection getConnection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        } else if (database != null) {
            // SQLite için direkt bağlantı
            return database.getConnection();
        }
        throw new SQLException("No database connection available");
    }

    /**
     * Veritabanı bağlantısını kapatır
     */
    public void close() {
        // DAO'ları kapat
        if (reportDAO != null) {
            try {
                reportDAO.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Error closing ReportDAO: " + e.getMessage());
            }
        }

        if (replayDAO != null) {
            try {
                replayDAO.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Error closing ReplayDAO: " + e.getMessage());
            }
        }

        // DataSource'u kapat (MySQL için)
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed");
        }

        // Database bağlantısını kapat
        if (database != null) {
            try {
                database.close();
                plugin.getLogger().info("Database connection closed");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing database", e);
            }
        }
    }

    /**
     * Veritabanı istatistiklerini döndürür
     */
    public DatabaseStats getStats() {
        DatabaseStats stats = new DatabaseStats();

        try {
            // Toplam rapor sayısı
            stats.totalReports = reportDAO.getTotalCount();

            // Bekleyen rapor sayısı
            stats.pendingReports = reportDAO.getPendingCount();

            // Kabul edilen rapor sayısı
            stats.acceptedReports = reportDAO.getAcceptedCount();

            // Reddedilen rapor sayısı
            stats.rejectedReports = reportDAO.getRejectedCount();

            // Toplam replay sayısı
            stats.totalReplays = replayDAO.getTotalCount();

            // Database boyutu
            if (database instanceof SQLiteDatabase) {
                File dbFile = new File(plugin.getDataFolder(), "reports.db");
                if (dbFile.exists()) {
                    stats.databaseSize = dbFile.length() / 1024.0 / 1024.0; // MB
                }
            } else if (dataSource != null) {
                // MySQL için pool istatistikleri
                stats.activeConnections = dataSource.getHikariPoolMXBean().getActiveConnections();
                stats.idleConnections = dataSource.getHikariPoolMXBean().getIdleConnections();
                stats.totalConnections = dataSource.getHikariPoolMXBean().getTotalConnections();
                stats.threadsAwaitingConnection = dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error getting database stats", e);
        }

        return stats;
    }

    /**
     * Veritabanını optimize eder
     */
    public void optimize() {
        plugin.getLogger().info("Starting database optimization...");

        try {
            if (database instanceof SQLiteDatabase) {
                // SQLite için VACUUM komutu
                try (Connection conn = getConnection();
                     var stmt = conn.createStatement()) {
                    stmt.execute("VACUUM");
                    plugin.getLogger().info("SQLite database optimized (VACUUM)");
                }
            } else if (database instanceof MySQLDatabase) {
                // MySQL için OPTIMIZE TABLE komutları
                try (Connection conn = getConnection();
                     var stmt = conn.createStatement()) {
                    stmt.execute("OPTIMIZE TABLE reports");
                    stmt.execute("OPTIMIZE TABLE replays");
                    plugin.getLogger().info("MySQL tables optimized");
                }
            }

            // Eski kayıtları temizle
            cleanOldRecords();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error optimizing database", e);
        }
    }

    /**
     * Eski kayıtları temizler
     */
    private void cleanOldRecords() throws SQLException {
        // Auto-close days
        int autoCloseDays = configManager.getAutoCloseDays();
        if (autoCloseDays > 0) {
            long cutoffTime = System.currentTimeMillis() - (autoCloseDays * 24L * 60L * 60L * 1000L);
            int closedCount = reportDAO.closeOldReports(cutoffTime);
            if (closedCount > 0) {
                plugin.getLogger().info("Auto-closed " + closedCount + " old reports");
            }
        }

        // DÜZELTİLDİ: Auto-delete replays
        int autoDeleteDays = configManager.getReplayAutoDeleteDays();
        if (autoDeleteDays > 0) {
            int deletedCount = replayDAO.deleteOldReplays(autoDeleteDays);
            if (deletedCount > 0) {
                plugin.getLogger().info("Deleted " + deletedCount + " old replays");
            }
        }
    }

    /**
     * Transaction başlatır
     */
    public void beginTransaction(Connection conn) throws SQLException {
        if (conn != null && !conn.getAutoCommit()) {
            conn.setAutoCommit(false);
        }
    }

    /**
     * Transaction'ı commit eder
     */
    public void commitTransaction(Connection conn) throws SQLException {
        if (conn != null && !conn.getAutoCommit()) {
            conn.commit();
            conn.setAutoCommit(true);
        }
    }

    /**
     * Transaction'ı rollback eder
     */
    public void rollbackTransaction(Connection conn) throws SQLException {
        if (conn != null && !conn.getAutoCommit()) {
            conn.rollback();
            conn.setAutoCommit(true);
        }
    }

    // Getter metodları
    public Database getDatabase() {
        return database;
    }

    public ReportDAO getReportDAO() {
        return reportDAO;
    }

    public ReplayDAO getReplayDAO() {
        return replayDAO;
    }

    public boolean isMySQL() {
        return database instanceof MySQLDatabase;
    }

    public boolean isSQLite() {
        return database instanceof SQLiteDatabase;
    }

    /**
     * Database istatistikleri için inner class
     */
    public static class DatabaseStats {
        public int totalReports = 0;
        public int pendingReports = 0;
        public int acceptedReports = 0;
        public int rejectedReports = 0;
        public int totalReplays = 0;
        public double databaseSize = 0.0; // MB

        // HikariCP stats (MySQL only)
        public int activeConnections = 0;
        public int idleConnections = 0;
        public int totalConnections = 0;
        public int threadsAwaitingConnection = 0;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Database Statistics ===\n");
            sb.append("Total Reports: ").append(totalReports).append("\n");
            sb.append("Pending: ").append(pendingReports).append("\n");
            sb.append("Accepted: ").append(acceptedReports).append("\n");
            sb.append("Rejected: ").append(rejectedReports).append("\n");
            sb.append("Total Replays: ").append(totalReplays).append("\n");

            if (databaseSize > 0) {
                sb.append("Database Size: ").append(String.format("%.2f MB", databaseSize)).append("\n");
            }

            if (totalConnections > 0) {
                sb.append("\n=== Connection Pool ===\n");
                sb.append("Active: ").append(activeConnections).append("\n");
                sb.append("Idle: ").append(idleConnections).append("\n");
                sb.append("Total: ").append(totalConnections).append("\n");
                sb.append("Waiting: ").append(threadsAwaitingConnection).append("\n");
            }

            return sb.toString();
        }
    }
}