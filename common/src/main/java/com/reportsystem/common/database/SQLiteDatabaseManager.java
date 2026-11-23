package com.reportsystem.common.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class SQLiteDatabaseManager {

    private final String dbPath;
    private Connection connection;
    // Paylaşılan bağlantının close() çağrısını yutan sarmalayıcı (try-with-resources güvenli)
    private Connection sharedProxy;
    private boolean shutdown = false;

    public SQLiteDatabaseManager(String dbPath) throws SQLException {
        this.dbPath = dbPath;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        // WAL mode etkinleştir (concurrent read desteği)
        try (Statement walStmt = connection.createStatement()) {
            walStmt.execute("PRAGMA journal_mode=WAL");
            walStmt.execute("PRAGMA busy_timeout=5000");
            // FK kapalı (SQLite varsayılanı): ağ modunda başka sunucunun rapor ID'siyle gelen replay
            // INSERT'i düşmesin; kanıt korunur. Rapor silme replay'i silmez (bilinçli).
            // PRAGMA bağlantı başınadır, DB dosyasına yazılmaz -> mevcut veritabanları etkilenmez.
        }

        createTables();
    }

    private void createTables() throws SQLException {
        // Reports tablosu
        String reportsTable = """
            CREATE TABLE IF NOT EXISTS reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reported_player_name TEXT NOT NULL,
                reporter_name TEXT NOT NULL,
                reporter_uuid TEXT NOT NULL,
                reason TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                status TEXT DEFAULT 'PENDING'
            )
        """;

        // Replays tablosu
        String replaysTable = """
            CREATE TABLE IF NOT EXISTS replays (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                report_id INTEGER NOT NULL,
                recorded_player TEXT NOT NULL,
                world_name TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                action_count INTEGER NOT NULL,
                data BLOB NOT NULL,
                compressed BOOLEAN DEFAULT TRUE,
                FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(reportsTable);
            stmt.execute(replaysTable);
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (shutdown) {
            throw new SQLException("SQLite veritabanı kapatıldı");
        }
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA busy_timeout=5000");
                // foreign_keys bilinçli olarak AÇILMIYOR (bkz. constructor açıklaması).
            }
            sharedProxy = null;
        }
        if (sharedProxy == null) {
            final Connection real = connection;
            sharedProxy = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    // close() no-op: paylaşılan bağlantı yalnızca manager.close() ile kapanır
                    if (method.getName().equals("close") && method.getParameterCount() == 0) {
                        return null;
                    }
                    if (method.getName().equals("isClosed") && method.getParameterCount() == 0) {
                        return real.isClosed();
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        }
        return sharedProxy;
    }

    public synchronized void close() throws SQLException {
        shutdown = true;
        sharedProxy = null;
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
