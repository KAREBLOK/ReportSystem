package com.reportsystem.common.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDatabaseManager {

    private final String dbPath;
    private Connection connection;

    public SQLiteDatabaseManager(String dbPath) throws SQLException {
        this.dbPath = dbPath;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        // WAL mode etkinleştir (concurrent read desteği)
        try (Statement walStmt = connection.createStatement()) {
            walStmt.execute("PRAGMA journal_mode=WAL");
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
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}