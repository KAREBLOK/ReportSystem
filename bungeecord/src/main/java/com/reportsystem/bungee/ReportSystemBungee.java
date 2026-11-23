package com.reportsystem.bungee;

import com.reportsystem.bungee.database.MySQLDatabase;
import com.reportsystem.bungee.listeners.BungeePluginMessageListener;
import com.reportsystem.bungee.listeners.ReportNotificationListener;
import com.reportsystem.bungee.punishment.BungeePunishmentManager;
import com.reportsystem.common.database.DatabaseConfig;
import com.reportsystem.common.database.MySQLReportDAO;
import com.reportsystem.common.database.ReportDAO;
import com.reportsystem.common.service.ReportService;
import com.reportsystem.common.messaging.MessageSigner;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReportSystemBungee extends Plugin {

    private static ReportSystemBungee instance;
    private Configuration config;
    private MySQLDatabase database;
    private BungeePunishmentManager punishmentManager;
    private ReportDAO reportDAO;
    private ReportService reportService;
    private ReportNotificationListener reportNotificationListener;
    private MessageSigner messageSigner;

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();
        initMessageSigner();
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

    /**
     * Plugin message HMAC anahtarini yukler; bossa rastgele uretip config'e kaydeder.
     */
    private void initMessageSigner() {
        // getString String olmayan degerde (or. tirnaksiz sayi) varsayilani dondurur -> mevcut anahtar "bos"
        // sanilip uzerine yazilirdi. Ham deger String.valueOf ile okunur.
        Object rawSecret = config.get("message-secret");
        String secret = rawSecret == null ? "" : String.valueOf(rawSecret);
        if (rawSecret != null && !(rawSecret instanceof String)) {
            getLogger().warning("[GUVENLIK] message-secret metin degil (" + rawSecret.getClass().getSimpleName()
                    + "); deger aynen kullaniliyor. config.yml'de tirnak icinde yazin.");
        }
        if (secret.trim().isEmpty()) {
            secret = MessageSigner.generateSecret();
            config.set("message-secret", secret);
            saveMessageSecret(secret);
            getLogger().warning("============================================================");
            getLogger().warning("[GUVENLIK] message-secret bos oldugu icin rastgele uretildi.");
            getLogger().warning("Ayni degeri TUM Spigot backend'lerin config.yml'indeki 'bungeecord.message-secret'");
            getLogger().warning("alanina kopyalayin; aksi halde mesajlar reddedilir.");
            getLogger().warning("============================================================");
        }
        this.messageSigner = new MessageSigner(secret.trim());
    }

    /**
     * Ust duzey "message-secret" degerini config.yml'e METIN olarak yazar (ConfigurationProvider.save
     * yorumlari sildigi icin kullanilmaz): satir varsa degistirir, yoksa dosyanin basina ekler.
     * Velocity'deki VelocityConfig.saveMessageSecret ile ayni algoritma.
     */
    private void saveMessageSecret(String secret) {
        Path configFile = new File(getDataFolder(), "config.yml").toPath();
        try {
            String text = Files.exists(configFile)
                    ? new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8)
                    : "";
            String line = "message-secret: \"" + secret + "\"";
            Pattern p = Pattern.compile("(?m)^message-secret:.*$");
            if (p.matcher(text).find()) {
                text = p.matcher(text).replaceFirst(Matcher.quoteReplacement(line));
            } else {
                text = line + System.lineSeparator() + text;
            }
            // Gecici dosyaya yaz + atomik tasi: yazim yarida kalirsa config.yml bozulmaz
            Path tmp = configFile.resolveSibling("config.yml.tmp");
            Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "message-secret config'e kaydedilemedi!", e);
        }
    }

    /** Backend'e imzali mesaj gonderir (HMAC-SHA256 zarfi, bkz. MessageSigner). */
    public void sendSigned(Server server, String channel, byte[] payload) {
        server.sendData(channel, messageSigner.sign(channel, payload));
    }

    /** Sunucu bilgisi uzerinden (oyuncu gerektirmeyen) imzali gonderim. */
    public void sendSigned(ServerInfo server, String channel, byte[] payload) {
        server.sendData(channel, messageSigner.sign(channel, payload));
    }

    public MessageSigner getMessageSigner() {
        return messageSigner;
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
                    config.getLong("database.mysql.pool.connection-timeout", 5000L),
                    600000L,
                    1800000L
            ).withSslMode(config.getString("database.mysql.ssl-mode", "DISABLED"));
            createDatabaseIfNotExists(dbConfig);
            this.database = new MySQLDatabase(dbConfig);
            createAllTables();
            this.reportDAO = new MySQLReportDAO(this.database.getDataSource());
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
        String url = String.format("jdbc:mysql://%s:%d/?sslMode=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                dbConfig.getHost(), dbConfig.getPort(), dbConfig.getSslMode());
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

            // Global punishments tablosu
            // NOT: MySQL partial index (WHERE active = TRUE) desteklemedigi icin
            // ayni oyuncuya birden fazla aktif ceza eklenebilir. Uygulama katmaninda kontrol edilmeli.
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
            getLogger().log(Level.SEVERE, "Tablo oluşturma hatası!", e);
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