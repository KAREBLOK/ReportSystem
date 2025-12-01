package com.reportsystem.common.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface Database {
    void connect() throws SQLException;
    void close() throws SQLException;
    Connection getConnection() throws SQLException;
    void createTables() throws SQLException;
}