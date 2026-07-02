package com.reportsystem.common.database;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class SQLiteDatabase implements Database {
    private final File dataFolder;
    private SQLiteDatabaseManager dbManager;
    private SQLiteReportDAO reportDAO;
    private SQLiteReplayDAO replayDAO;

    public SQLiteDatabase(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    @Override
    public void connect() throws SQLException {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "reportsystem.db");
        this.dbManager = new SQLiteDatabaseManager(dbFile.getAbsolutePath());

        // DAO'ları oluştur (paylaşılan connection manager kullanarak)
        this.reportDAO = new SQLiteReportDAO(dbManager);
        this.replayDAO = new SQLiteReplayDAO(dbManager);
    }

    @Override
    public void close() throws SQLException {
        if (reportDAO != null) {
            reportDAO.close();
        }
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dbManager.getConnection();
    }

    @Override
    public void createTables() throws SQLException {
        // SQLiteDatabaseManager zaten tabloları oluşturuyor
    }

    public SQLiteReportDAO getReportDAO() {
        return reportDAO;
    }

    public SQLiteReplayDAO getReplayDAO() {
        return replayDAO;
    }
}