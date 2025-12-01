package com.reportsystem.bungee;

import com.reportsystem.bungee.database.MySQLDatabase;
import com.reportsystem.bungee.listeners.BungeePluginMessageListener;
import com.reportsystem.bungee.listeners.ReportNotificationListener;
import com.reportsystem.bungee.punishment.BungeePunishmentManager;
import com.reportsystem.common.database.DatabaseConfig;
import com.reportsystem.common.database.MySQLReportDAO;
import com.reportsystem.common.database.ReportDAO;
import com.reportsystem.common.service.ReportService;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.*;
import java.util.logging.Level;

public class ReportSystemBungee extends Plugin {

    private static ReportSystemBungee instance;
    private Configuration config;
    private MySQLDatabase database;
    private BungeePunishmentManager punishmentManager;
    private ReportDAO reportDAO;
    private ReportService reportService;
    private ReportNotificationListener reportNotificationListener;

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();
        if (!setupDatabase()) {
            getLogger().severe("Veritabanı başlatılamadı! Eklenti devre dışı bırakılıyor.");
            return;
        }
        getProxy().registerChannel("reportsystem:channel");
        getProxy().registerChannel("reportsystem:notification");
        getProxy().getPluginManager().registerListener(this, new BungeePluginMessageListener(this));
        this.reportNotificationListener = new ReportNotificationListener(this);
        getProxy().getPluginManager().registerListener(this, reportNotificationListener);
        getLogger().info("ReportSystem-BungeeCord (Punishment Sync + Notifications) başarıyla yüklendi!");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        if (reportDAO instanceof MySQLReportDAO) {
            ((MySQLReportDAO) reportDAO).close();
        }
        getProxy().unregisterChannel("reportsystem:channel");
        getLogger().info("ReportSystem-BungeeCord kapatıldı!");
    }

    private void loadConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Config dosyası oluşturulamadı!", e);
            }
        }
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Config dosyası yüklenemedi!", e);
        }
    }

    private boolean setupDatabase() {
        try {
            DatabaseConfig dbConfig = new DatabaseConfig(
                    config.getString("database.mysql.host", "localhost"),
                    config.getInt("database.mysql.port", 3306),
                    config.getString("database.mysql.database", "reportsystem"),
                    config.getString("database.mysql.username", "root"),
                    config.getString("database.mysql.password", "")
            );
            createDatabaseIfNotExists(dbConfig);
            this.database = new MySQLDatabase(dbConfig);
            createAllTables();
            this.reportDAO = new MySQLReportDAO(
                    dbConfig.getHost(),
                    dbConfig.getPort(),
                    dbConfig.getDatabase(),
                    dbConfig.getUsername(),
                    dbConfig.getPassword()
            );
            this.reportService = new ReportService(reportDAO);
            this.punishmentManager = new BungeePunishmentManager(this);
            getProxy().getPluginManager().registerListener(this, punishmentManager);
            getLogger().info("MySQL veritabanı bağlantısı başarılı!");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Veritabanı hatası!", e);
            return false;
        }
    }

    private void createDatabaseIfNotExists(DatabaseConfig dbConfig) {
        String url = String.format("jdbc:mysql://%s:%d/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                dbConfig.getHost(), dbConfig.getPort());
        try (Connection conn = DriverManager.getConnection(url, dbConfig.getUsername(), dbConfig.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbConfig.getDatabase() + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            getLogger().severe("Veritabanı oluşturma hatası: " + e.getMessage());
        }
    }

    private void createAllTables() {
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {

            // Reports tablosu (Doğru)
            String reportsTable = """
                CREATE TABLE IF NOT EXISTS reports (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    reported_player_name VARCHAR(16) NOT NULL,
                    reporter_name VARCHAR(16) NOT NULL,
                    reporter_uuid VARCHAR(36) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    timestamp BIGINT NOT NULL,
                    status VARCHAR(20) DEFAULT 'PENDING',
                    server_name VARCHAR(50),
                    punished BOOLEAN DEFAULT FALSE,
                    punishment_type VARCHAR(20),
                    INDEX idx_reported (reported_player_name),
                    INDEX idx_reporter (reporter_name),
                    INDEX idx_status (status),
                    INDEX idx_timestamp (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;
            stmt.execute(reportsTable);

            // Global punishments tablosu (Doğru)
            String punishmentsTable = """
                CREATE TABLE IF NOT EXISTS global_punishments (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_name VARCHAR(32) NOT NULL,
                    player_uuid VARCHAR(36),
                    punishment_type VARCHAR(20) NOT NULL,
                    reason TEXT NOT NULL,
                    punisher VARCHAR(32) NOT NULL,
                    start_time BIGINT NOT NULL,
                    end_time BIGINT,
                    active BOOLEAN DEFAULT TRUE,
                    server_origin VARCHAR(50),
                    INDEX idx_player_punishments (player_name, active),
                    INDEX idx_uuid_punishments (player_uuid, active),
                    INDEX idx_active_punishments (active, punishment_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;
            stmt.execute(punishmentsTable);

            // DÜZELTİLDİ: Replay tablosu, eksik kolonlar eklendi
            String replayTable = """
                CREATE TABLE IF NOT EXISTS replays (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    report_id INT NOT NULL,
                    recorded_player VARCHAR(16) NOT NULL,
                    recorded_player_uuid VARCHAR(36) NOT NULL,
                    duration BIGINT NOT NULL,
                    created_at BIGINT NOT NULL,
                    action_count INT NOT NULL,
                    view_count INT DEFAULT 0,
                    data LONGBLOB NOT NULL,
                    compressed BOOLEAN DEFAULT TRUE,
                    INDEX idx_report_id (report_id),
                    INDEX idx_player (recorded_player),
                    INDEX idx_created_at (created_at),
                    FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;
            stmt.execute(replayTable);

            getLogger().info("Tüm BungeeCord tabloları başarıyla kontrol edildi/oluşturuldu!");

        } catch (SQLException e) {
            getLogger().severe("Tablo oluşturma hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static ReportSystemBungee getInstance() {
        return instance;
    }
    public Configuration getConfig() {
        return config;
    }
    public MySQLDatabase getDatabase() {
        return database;
    }
    public BungeePunishmentManager getPunishmentManager() {
        return punishmentManager;
    }
    public ReportDAO getReportDAO() {
        return reportDAO;
    }
    public ReportService getReportService() {
        return reportService;
    }
    public ReportNotificationListener getReportNotificationListener() {
        return reportNotificationListener;
    }
}