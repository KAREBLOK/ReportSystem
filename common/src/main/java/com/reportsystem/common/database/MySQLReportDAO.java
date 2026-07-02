package com.reportsystem.common.database;

import com.reportsystem.common.models.Report;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MySQLReportDAO implements ReportDAO {

    private final HikariDataSource dataSource;

    public MySQLReportDAO(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        createTables();
    }

    public void createTables() {
        String sql = """
            CREATE TABLE IF NOT EXISTS reports (
                id INT AUTO_INCREMENT PRIMARY KEY,
                reported_player_name VARCHAR(16) NOT NULL,
                reporter_name VARCHAR(16) NOT NULL,
                reporter_uuid VARCHAR(36) NOT NULL,
                reason TEXT NOT NULL,
                timestamp BIGINT NOT NULL,
                status VARCHAR(20) DEFAULT 'PENDING',
                server_name VARCHAR(50) DEFAULT NULL,
                punished BOOLEAN DEFAULT FALSE,
                punishment_type VARCHAR(20),
                INDEX idx_reported (reported_player_name),
                INDEX idx_reporter (reporter_name),
                INDEX idx_status (status),
                INDEX idx_timestamp (timestamp),
                INDEX idx_server (server_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);

            // Mevcut tabloya yeni kolonları ekle (eğer yoksa)
            try {
                stmt.execute("ALTER TABLE reports ADD COLUMN server_name VARCHAR(50) DEFAULT NULL");
            } catch (SQLException e) {
                // Kolon zaten varsa ignore
            }

            try {
                stmt.execute("ALTER TABLE reports ADD COLUMN punished BOOLEAN DEFAULT FALSE");
            } catch (SQLException e) {
                // Kolon zaten varsa ignore
            }

            try {
                stmt.execute("ALTER TABLE reports ADD COLUMN punishment_type VARCHAR(20)");
            } catch (SQLException e) {
                // Kolon zaten varsa ignore
            }

            try {
                stmt.execute("ALTER TABLE reports ADD COLUMN reporter_notified BOOLEAN DEFAULT FALSE");
            } catch (SQLException e) {
                // Kolon zaten varsa ignore
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Report createReport(String reportedPlayerName, String reporterName, UUID reporterUUID, String reason) {
        String sql = "INSERT INTO reports (reported_player_name, reporter_name, reporter_uuid, reason, timestamp) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, reportedPlayerName);
            stmt.setString(2, reporterName);
            stmt.setString(3, reporterUUID.toString());
            stmt.setString(4, reason);
            stmt.setLong(5, System.currentTimeMillis());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        return getReport(id);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Report getReport(int id) {
        String sql = "SELECT * FROM reports WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return extractReportFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Report> getReports(int page, int perPage) {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports ORDER BY timestamp DESC LIMIT ? OFFSET ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, perPage);
            stmt.setInt(2, (page - 1) * perPage);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(extractReportFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    @Override
    public List<Report> getAllReports() {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports ORDER BY timestamp DESC";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                reports.add(extractReportFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    @Override
    public List<Report> getReportsByPlayer(String playerName) {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE reported_player_name = ? ORDER BY timestamp DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(extractReportFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    @Override
    public List<Report> getReportsByReporter(String reporterName) {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE reporter_name = ? ORDER BY timestamp DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reporterName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(extractReportFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    @Override
    public int getReportCount() {
        String sql = "SELECT COUNT(*) FROM reports";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean updateReportStatus(int id, String status) {
        String sql = "UPDATE reports SET status = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setInt(2, id);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean updateReportServer(int id, String serverName) {
        String sql = "UPDATE reports SET server_name = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serverName);
            stmt.setInt(2, id);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean updatePunishmentStatus(int reportId, String punishmentType) throws SQLException {
        String sql = "UPDATE reports SET punished = true, punishment_type = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, punishmentType);
            stmt.setInt(2, reportId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deleteReport(int id) {
        String sql = "DELETE FROM reports WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void deleteOldReports(int days) {
        String sql = "DELETE FROM reports WHERE timestamp < ?";
        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, cutoffTime); // BUG FIX: days yerine cutoffTime kullanılmalı
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                System.out.println(deleted + " eski rapor silindi.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getTotalCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM reports";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    @Override
    public int getPendingCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM reports WHERE status = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "PENDING");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    @Override
    public int getAcceptedCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM reports WHERE status = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "ACCEPTED");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    @Override
    public int getRejectedCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM reports WHERE status = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "REJECTED");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    @Override
    public int closeOldReports(long cutoffTime) throws SQLException {
        String sql = "UPDATE reports SET status = ? WHERE status = ? AND timestamp < ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "CLOSED");
            ps.setString(2, "PENDING");
            ps.setLong(3, cutoffTime);

            return ps.executeUpdate();
        }
    }

    private Report extractReportFromResultSet(ResultSet rs) throws SQLException {
        Report report = new Report();
        report.setId(rs.getInt("id"));
        report.setReportedPlayerName(rs.getString("reported_player_name"));
        report.setReporterName(rs.getString("reporter_name"));
        report.setReporterUuid(rs.getString("reporter_uuid"));
        report.setReason(rs.getString("reason"));
        report.setTimestamp(rs.getLong("timestamp"));
        report.setStatus(rs.getString("status"));

        // Server name'i oku
        String serverName = rs.getString("server_name");
        if (serverName != null) {
            report.setServerName(serverName);
        }

        // Ceza bilgilerini oku
        try {
            report.setPunished(rs.getBoolean("punished"));
            report.setPunishmentType(rs.getString("punishment_type"));
        } catch (SQLException e) {
            // Eski tablolarda bu kolonlar olmayabilir
        }

        // Raporcu bildirim durumunu oku
        try {
            report.setReporterNotified(rs.getBoolean("reporter_notified"));
        } catch (SQLException e) {
            // Eski tablolarda bu kolon olmayabilir
        }

        return report;
    }

    @Override
    public List<Report> getReportsByStatus(String status) {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE status = ? ORDER BY timestamp DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(extractReportFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    @Override
    public List<Report> getReportsByServer(String serverName) {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE server_name = ? ORDER BY timestamp DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serverName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(extractReportFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    @Override
    public List<Report> getReportsByDateRange(long startTime, long endTime) {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(extractReportFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    @Override
    public int countReportsByReporterAndReported(String reporterName, String reportedName) {
        // Sadece PENDING raporları say - yetkili kabul/reddettiğinde yeni rapor oluşturulabilsin
        String sql = "SELECT COUNT(*) FROM reports WHERE reporter_name = ? AND reported_player_name = ? AND status = 'PENDING'";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reporterName);
            stmt.setString(2, reportedName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public int getTotalReportsForPlayer(String playerName) {
        String sql = "SELECT COUNT(*) FROM reports WHERE reported_player_name = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public List<Report> getUnnotifiedReportsForReporter(String reporterUuid) throws SQLException {
        String sql = "SELECT * FROM reports WHERE reporter_uuid = ? AND status = 'ACCEPTED' AND (reporter_notified = FALSE OR reporter_notified IS NULL) ORDER BY timestamp DESC";
        List<Report> reports = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reporterUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(extractReportFromResultSet(rs));
                }
            }
        }
        return reports;
    }

    @Override
    public void markReporterNotified(int reportId) throws SQLException {
        String sql = "UPDATE reports SET reporter_notified = TRUE WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reportId);
            stmt.executeUpdate();
        }
    }

    public void close() {
        // Pool kapatma MySQLDatabase tarafından yönetilir
    }
}