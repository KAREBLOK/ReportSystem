package com.reportsystem.common.database;

import com.reportsystem.common.models.Replay;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// NOT: SQLite ve MySQL replay semalari uyumsuz. Veritabani tipi degistirmek veri kaybina neden olur.
public class MySQLReplayDAO implements ReplayDAO {

    private final HikariDataSource dataSource;

    public MySQLReplayDAO(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        createTables();
    }

    private void createTables() {
        String sql = """
            CREATE TABLE IF NOT EXISTS replays (
                id INT AUTO_INCREMENT PRIMARY KEY,
                report_id INT NOT NULL,
                recorded_player VARCHAR(16) NOT NULL,
                recorded_player_uuid VARCHAR(36) NOT NULL,
                world_name VARCHAR(64) NOT NULL DEFAULT 'world',
                duration BIGINT NOT NULL,
                created_at BIGINT NOT NULL,
                action_count INT NOT NULL,
                view_count INT DEFAULT 0,
                data LONGBLOB NOT NULL,
                compressed BOOLEAN DEFAULT TRUE,
                INDEX idx_report_id (report_id),
                INDEX idx_player (recorded_player),
                INDEX idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            // Mevcut tabloya world_name kolonu ekle (yoksa)
            try {
                stmt.execute("ALTER TABLE replays ADD COLUMN world_name VARCHAR(64) NOT NULL DEFAULT 'world' AFTER recorded_player_uuid");
            } catch (SQLException ignored) {
                // Kolon zaten varsa hata verir, sessizce devam et
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveReplay(Replay replay) throws SQLException {
        String sql = "INSERT INTO replays(report_id, recorded_player, recorded_player_uuid, world_name, duration, " +
                "created_at, action_count, view_count, data, compressed) VALUES(?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, replay.getReportId());
            pstmt.setString(2, replay.getRecordedPlayer());
            pstmt.setString(3, replay.getRecordedPlayerUuid());
            pstmt.setString(4, replay.getWorldName());
            pstmt.setLong(5, replay.getDuration());
            pstmt.setLong(6, replay.getCreatedAt());
            pstmt.setInt(7, replay.getActionCount());
            pstmt.setInt(8, replay.getViewCount());
            pstmt.setBytes(9, replay.getData());
            pstmt.setBoolean(10, replay.isCompressed());

            pstmt.executeUpdate();
        }
    }

    @Override
    public Optional<Replay> getReplayByReportId(int reportId) throws SQLException {
        String sql = "SELECT * FROM replays WHERE report_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, reportId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToReplay(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Replay> getReplayById(int id) throws SQLException {
        String sql = "SELECT * FROM replays WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToReplay(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Replay> getReplays(int page, int pageSize) throws SQLException {
        List<Replay> replays = new ArrayList<>();
        String sql = "SELECT * FROM replays ORDER BY created_at DESC LIMIT ? OFFSET ?";
        int offset = (page - 1) * pageSize;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, pageSize);
            pstmt.setInt(2, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    replays.add(mapResultSetToReplay(rs));
                }
            }
        }
        return replays;
    }

    @Override
    public List<Replay> getReplaysByPlayer(String playerName) throws SQLException {
        List<Replay> replays = new ArrayList<>();
        String sql = "SELECT * FROM replays WHERE recorded_player = ? ORDER BY created_at DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerName);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    replays.add(mapResultSetToReplay(rs));
                }
            }
        }
        return replays;
    }

    @Override
    public void deleteReplay(int id) throws SQLException {
        String sql = "DELETE FROM replays WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public int deleteOldReplays(int daysOld) throws SQLException {
        String sql = "DELETE FROM replays WHERE created_at < ?";
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, cutoffTime);
            return pstmt.executeUpdate();
        }
    }

    @Override
    public int getTotalCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM replays";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private Replay mapResultSetToReplay(ResultSet rs) throws SQLException {
        Replay replay = new Replay(
                rs.getInt("id"),
                rs.getInt("report_id"),
                rs.getString("recorded_player"),
                rs.getString("recorded_player_uuid"),
                rs.getLong("duration"),
                rs.getLong("created_at"),
                rs.getInt("action_count"),
                rs.getInt("view_count"),
                rs.getBytes("data"),
                rs.getBoolean("compressed")
        );
        replay.setWorldName(rs.getString("world_name"));
        return replay;
    }

    public void close() {
        // Pool kapatma MySQLDatabase tarafından yönetilir
    }
}