package com.reportsystem.spigot.punishment.providers;

import com.reportsystem.common.database.Database;
import com.reportsystem.common.database.SQLiteDatabase;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Dahili ceza sağlayıcısının mute ve uyarılarını eklentinin mevcut veritabanında (SQLite/MySQL) saklar.
 *
 * Tüm G/Ç tek bir arka plan thread'inde SIRAYLA çalışır (ana thread asla DB'ye dokunmaz; mute->unmute
 * gibi sıralı işlemler karışmaz). Okuma sorguları bellekteki önbellekten yapılır (bkz. InternalPunishmentProvider).
 *
 * Tablolar (SQLite + MySQL 8 uyumlu, ortak sözdizimi; ayrılmış kelimeler backtick'li):
 *   rs_mutes(player_name PK, expires, reason, staff, created)
 *   rs_warnings(player_name, reason, staff, created)
 */
final class PunishmentStore {

    /** Yüklenen mute kaydı. */
    record MuteRow(String player, long expires) { }

    /** Yüklenen uyarı kaydı. */
    record WarnRow(String player, String reason, String staff, long created) { }

    private final JavaPlugin plugin;
    private final Database database;
    private final boolean sqlite;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ReportSystem-PunishmentIO");
        t.setDaemon(true);
        return t;
    });

    PunishmentStore(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.sqlite = database instanceof SQLiteDatabase;
    }

    /** Tabloları oluşturur ve yükleme sonucunu callback'e (arka plan thread'inde) verir. */
    void initAsync(java.util.function.Consumer<List<MuteRow>> onMutes, java.util.function.Consumer<List<WarnRow>> onWarns) {
        io.execute(() -> {
            try {
                createTables();
                purgeExpired();
                onMutes.accept(loadMutes());
                onWarns.accept(loadWarnings());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[PunishmentStore] Yükleme hatası", e);
            }
        });
    }

    void saveMute(String player, long expires, String reason, String staff) {
        String name = player.toLowerCase(Locale.ROOT);
        io.execute(() -> run("saveMute", conn -> {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM `rs_mutes` WHERE `player_name` = ?")) {
                del.setString(1, name);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO `rs_mutes` (`player_name`, `expires`, `reason`, `staff`, `created`) VALUES (?, ?, ?, ?, ?)")) {
                ins.setString(1, name);
                ins.setLong(2, expires);
                ins.setString(3, reason);
                ins.setString(4, staff);
                ins.setLong(5, System.currentTimeMillis());
                ins.executeUpdate();
            }
        }));
    }

    void deleteMute(String player) {
        String name = player.toLowerCase(Locale.ROOT);
        io.execute(() -> run("deleteMute", conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM `rs_mutes` WHERE `player_name` = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        }));
    }

    void purgeExpiredAsync() {
        io.execute(() -> run("purgeExpired", this::purgeExpiredOn));
    }

    void addWarning(String player, String reason, String staff, long created) {
        String name = player.toLowerCase(Locale.ROOT);
        io.execute(() -> run("addWarning", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO `rs_warnings` (`player_name`, `reason`, `staff`, `created`) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, reason);
                ps.setString(3, staff);
                ps.setLong(4, created);
                ps.executeUpdate();
            }
        }));
    }

    /** Bekleyen yazmaları bitirip I/O thread'ini kapatır (eklenti kapanışında çağrılabilir). */
    void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- dahili

    @FunctionalInterface
    private interface SqlWork {
        void apply(Connection conn) throws SQLException;
    }

    /** Bağlantı alır, işi yapar; SQLite'ta paylaşılan bağlantıyı KAPATMAZ (diğer DAO'lar kullanıyor). */
    private void run(String what, SqlWork work) {
        Connection conn = null;
        try {
            conn = database.getConnection();
            if (sqlite) {
                synchronized (conn) {
                    work.apply(conn);
                }
            } else {
                work.apply(conn);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PunishmentStore] " + what + " başarısız", e);
        } finally {
            if (!sqlite && conn != null) {
                try {
                    conn.close(); // havuza iade
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private void createTables() {
        run("createTables", conn -> {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS `rs_mutes` ("
                        + "`player_name` VARCHAR(64) NOT NULL PRIMARY KEY, "
                        + "`expires` BIGINT NOT NULL, "
                        + "`reason` TEXT, "
                        + "`staff` VARCHAR(64), "
                        + "`created` BIGINT NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS `rs_warnings` ("
                        + "`player_name` VARCHAR(64) NOT NULL, "
                        + "`reason` TEXT, "
                        + "`staff` VARCHAR(64), "
                        + "`created` BIGINT NOT NULL)");
            }
        });
    }

    private void purgeExpired() {
        run("purgeExpired", this::purgeExpiredOn);
    }

    private void purgeExpiredOn(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM `rs_mutes` WHERE `expires` <> ? AND `expires` < ?")) {
            ps.setLong(1, Long.MAX_VALUE);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private List<MuteRow> loadMutes() {
        List<MuteRow> rows = new ArrayList<>();
        run("loadMutes", conn -> {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT `player_name`, `expires` FROM `rs_mutes`")) {
                while (rs.next()) {
                    rows.add(new MuteRow(rs.getString(1), rs.getLong(2)));
                }
            }
        });
        return rows;
    }

    private List<WarnRow> loadWarnings() {
        List<WarnRow> rows = new ArrayList<>();
        run("loadWarnings", conn -> {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT `player_name`, `reason`, `staff`, `created` FROM `rs_warnings` ORDER BY `created` ASC")) {
                while (rs.next()) {
                    rows.add(new WarnRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
                }
            }
        });
        return rows;
    }
}
