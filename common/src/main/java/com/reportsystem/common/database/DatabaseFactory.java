package com.reportsystem.common.database;

import java.io.File;
import java.sql.SQLException;

public class DatabaseFactory {

    public static Database createDatabase(String type, DatabaseConfig config) throws SQLException {
        if (type.equalsIgnoreCase("mysql")) {
            return new MySQLDatabase(config);
        } else {
            return new SQLiteDatabase(config.getDataFolder());
        }
    }
}