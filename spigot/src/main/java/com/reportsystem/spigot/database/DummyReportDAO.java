package com.reportsystem.spigot.database;

import com.reportsystem.common.database.ReportDAO;
import com.reportsystem.common.models.Report;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DummyReportDAO implements ReportDAO {

    @Override
    public Report createReport(String reportedPlayerName, String reporterName, UUID reporterUUID, String reason) {
        Report report = new Report();
        report.setId(1);
        report.setReportedPlayerName(reportedPlayerName);
        report.setReporterName(reporterName);
        report.setReporterUuid(reporterUUID.toString());
        report.setReason(reason);
        return report;
    }

    @Override
    public Report getReport(int id) {
        return null;
    }

    @Override
    public List<Report> getReports(int page, int perPage) {
        return new ArrayList<>();
    }

    @Override
    public List<Report> getAllReports() {
        return new ArrayList<>();
    }

    @Override
    public List<Report> getReportsByPlayer(String playerName) {
        return new ArrayList<>();
    }

    @Override
    public List<Report> getReportsByReporter(String reporterName) {
        return new ArrayList<>();
    }

    @Override
    public int getReportCount() {
        return 0;
    }

    @Override
    public boolean updateReportStatus(int id, String status) {
        return true;
    }

    @Override
    public boolean updateReportServer(int id, String serverName) {
        return true;
    }

    @Override
    public boolean updatePunishmentStatus(int reportId, String punishmentType) {
        return true;
    }

    @Override
    public boolean deleteReport(int id) {
        return true;
    }

    @Override
    public void deleteOldReports(int days) {
        // Dummy implementation
    }

    @Override
    public int getTotalCount() throws SQLException {
        return 0;
    }

    @Override
    public int getPendingCount() throws SQLException {
        return 0;
    }

    @Override
    public int getAcceptedCount() throws SQLException {
        return 0;
    }

    @Override
    public int getRejectedCount() throws SQLException {
        return 0;
    }

    @Override
    public int closeOldReports(long cutoffTime) throws SQLException {
        return 0;
    }

    @Override
    public void close() throws SQLException {
        // Dummy implementation
    }

    @Override
    public int countReportsByReporterAndReported(String reporterName, String reportedName) {
        return 0; // Dummy always returns 0 (no limit check)
    }

    @Override
    public int getTotalReportsForPlayer(String playerName) {
        return 0; // Dummy always returns 0
    }
}