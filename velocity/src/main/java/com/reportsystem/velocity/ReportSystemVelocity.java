package com.reportsystem.velocity;

import com.google.inject.Inject;
import com.reportsystem.velocity.config.VelocityConfig;
import com.reportsystem.velocity.database.MySQLDatabase;
import com.reportsystem.velocity.listeners.ReportNotificationListener;
import com.reportsystem.velocity.listeners.VelocityPluginMessageListener;
import com.reportsystem.velocity.punishment.VelocityPunishmentManager;
import com.reportsystem.common.database.DatabaseConfig;
import com.reportsystem.common.database.MySQLReportDAO;
import com.reportsystem.common.database.ReportDAO;
import com.reportsystem.common.service.ReportService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.reportsystem.common.messaging.MessageSigner;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.*;

@Plugin(
        id = "reportsystem",
        name = "ReportSystem-Velocity",
        version = "2.2.0", // pom.xml sürümüyle aynı tutulmalı (REL-01)
        authors = {"KAREBLOK"},
        description = "Cross-server report system for Velocity"
)
public class ReportSystemVelocity {

    private static ReportSystemVelocity instance;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityConfig config;
    private MySQLDatabase database;
    private VelocityPunishmentManager punishmentManager;
    private ReportDAO reportDAO;
    private ReportService reportService;
    private ReportNotificationListener reportNotificationListener;
    private MessageSigner messageSigner;

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("reportsystem:channel");
    public static final MinecraftChannelIdentifier NOTIFICATION_CHANNEL =
            MinecraftChannelIdentifier.from("reportsystem:notification");

    @Inject
    public ReportSystemVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        config = new VelocityConfig(dataDirectory, logger);
        config.load();
        initMessageSigner();

        if (!setupDatabase()) {
            logger.error("Veritabani baslatilamadi! Eklenti devre disi birakiliyor.");
            return;
        }

        // Kanal kayitlari
        server.getChannelRegistrar().register(CHANNEL);
        server.getChannelRegistrar().register(NOTIFICATION_CHANNEL);

        // Listener'lari kaydet
        server.getEventManager().register(this, new VelocityPluginMessageListener(this));
        this.reportNotificationListener = new ReportNotificationListener(this);
        server.getEventManager().register(this, reportNotificationListener);

        // Not: /report komutu Velocity'de kayitli DEGIL.
        // Rapor komutu Spigot tarafinda calisir (GUI destegi ile).

        logger.info("ReportSystem-Velocity (Punishment Sync + Notifications) basariyla yuklendi!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.close();
        }
        if (reportDAO instanceof MySQLReportDAO) {
            ((MySQLReportDAO) reportDAO).close();
        }
        server.getChannelRegistrar().unregister(CHANNEL);
        server.getChannelRegistrar().unregister(NOTIFICATION_CHANNEL);
        logger.info("ReportSystem-Velocity kapatildi!");
    }

    /**
     * Plugin message HMAC anahtarini yukler; bossa rastgele uretip config'e kaydeder.
     */
    private void initMessageSigner() {
        // Tirnaksiz sayisal deger (1e5 -> 100000.0): getMessageSecret uyari verir, Spigot/Bungee ile ayni toString degerini dondurur
        String secret = config.getMessageSecret();
        if (secret == null || secret.trim().isEmpty()) {
            secret = MessageSigner.generateSecret();
            config.saveMessageSecret(secret);
            logger.warn("============================================================");
            logger.warn("[GUVENLIK] message-secret bos oldugu icin rastgele uretildi.");
            logger.warn("Ayni degeri TUM Spigot backend'lerin config.yml'indeki 'bungeecord.message-secret'");
            logger.warn("alanina kopyalayin; aksi halde mesajlar reddedilir.");
            logger.warn("============================================================");
        }
        this.messageSigner = new MessageSigner(secret.trim());
    }

    public MessageSigner getMessageSigner() {
        return messageSigner;
    }

    /** Backend'e imzali mesaj gonderir (HMAC-SHA256 zarfi, bkz. MessageSigner). */
    public void sendSigned(RegisteredServer target, ChannelIdentifier channel, byte[] payload) {
        target.sendPluginMessage(channel, messageSigner.sign(channel.getId(), payload));
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
            // Havuz + SSL (eski config'te anahtar yoksa varsayilan: havuz 10, timeout 5000 ms, SSL kapali)
            dbConfig.withPool(
                    config.getInt("database.mysql.pool.maximum-pool-size", 10),
                    2,
                    config.getInt("database.mysql.pool.connection-timeout", 5000),
                    600000L,
                    1800000L
            ).withSslMode(config.getString("database.mysql.ssl-mode", "DISABLED"));

            createDatabaseIfNotExists(dbConfig);
            this.database = new MySQLDatabase(dbConfig);
            createAllTables();
            this.reportDAO = new MySQLReportDAO(this.database.getDataSource());
            this.reportService = new ReportService(reportDAO);
            this.punishmentManager = new VelocityPunishmentManager(this);
            server.getEventManager().register(this, punishmentManager);

            logger.info("MySQL veritabani baglantisi basarili!");
            return true;
        } catch (Exception e) {
            logger.error("Veritabani hatasi!", e);
            return false;
        }
    }

    private void createDatabaseIfNotExists(DatabaseConfig dbConfig) {
        // Velocity'nin izole classloader'inda driver'i manuel yukle
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.error("MySQL driver bulunamadi!", e);
            return;
        }

        String url = String.format("jdbc:mysql://%s:%d/?sslMode=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                dbConfig.getHost(), dbConfig.getPort(), dbConfig.getSslMode());
        try (Connection conn = DriverManager.getConnection(url, dbConfig.getUsername(), dbConfig.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbConfig.getDatabase() +
                    " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            logger.error("Veritabani olusturma hatasi: {}", e.getMessage());
        }
    }

    private void createAllTables() {
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {

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

            logger.info("Tum Velocity tablolari basariyla kontrol edildi/olusturuldu!");

        } catch (SQLException e) {
            logger.error("Tablo olusturma hatasi: {}", e.getMessage(), e);
        }
    }

    public static ReportSystemVelocity getInstance() {
        return instance;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public VelocityConfig getConfig() {
        return config;
    }

    public MySQLDatabase getDatabase() {
        return database;
    }

    public VelocityPunishmentManager getPunishmentManager() {
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
