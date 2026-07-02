package com.reportsystem.bungee.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteDatabase {

    private final String dbPath;
    private Connection connection;

    public SQLiteDatabase(String dbPath) throws SQLException {
        this.dbPath = dbPath;

        // SQLite driver'ını yükle - relocated package ile
        try {
            Class.forName("com.reportsystem.bungee.libs.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            // Eğer relocated class bulunamazsa normal class'ı dene
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e2) {
                throw new SQLException("SQLite JDBC driver bulunamadı!", e2);
            }
        }

        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        // WAL mode etkinleştir (concurrent read desteği)
        try (java.sql.Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("SQLite connection close error: " + e.getMessage());
        }
    }
}