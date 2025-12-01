package com.reportsystem.common.database;

import java.io.File;

public class DatabaseConfig {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final File dataFolder;

    // MySQL constructor
    public DatabaseConfig(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.dataFolder = null;
    }

    // SQLite constructor
    public DatabaseConfig(File dataFolder) {
        this.host = null;
        this.port = 0;
        this.database = null;
        this.username = null;
        this.password = null;
        this.dataFolder = dataFolder;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public String getJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
    }
}