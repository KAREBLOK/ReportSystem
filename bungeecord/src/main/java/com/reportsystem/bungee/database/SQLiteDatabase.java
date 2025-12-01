package com.reportsystem.bungee.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteDatabase {

    private final Connection connection;

    public SQLiteDatabase(String dbPath) throws SQLException {
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
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}