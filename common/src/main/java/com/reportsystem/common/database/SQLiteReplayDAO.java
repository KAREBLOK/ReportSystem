package com.reportsystem.common.database;

import com.reportsystem.common.models.Replay;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// NOT: SQLite ve MySQL replay semalari uyumsuz. Veritabani tipi degistirmek veri kaybina neden olur.
public class SQLiteReplayDAO implements ReplayDAO {

    private final SQLiteDatabaseManager dbManager;

    public SQLiteReplayDAO(SQLiteDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void saveReplay(Replay replay) throws SQLException {
        String sql = "INSERT INTO replays(report_id, recorded_player, world_name, start_time, " +
                "end_time, duration, action_count, data, compressed) VALUES(?,?,?,?,?,?,?,?,?)";

        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, replay.getReportId());
            pstmt.setString(2, replay.getRecordedPlayer());
            pstmt.setString(3, replay.getWorldName());
            pstmt.setLong(4, replay.getStartTime());
            pstmt.setLong(5, replay.getEndTime());
            pstmt.setLong(6, replay.getDuration());
            pstmt.setInt(7, replay.getActionCount());
            pstmt.setBytes(8, replay.getData());
            pstmt.setBoolean(9, replay.isCompressed());

            pstmt.executeUpdate();
        }
    }

    @Override
    public Optional<Replay> getReplayByReportId(int reportId) throws SQLException {
        String sql = "SELECT * FROM replays WHERE report_id = ?";

        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

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

        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

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
        String sql = "SELECT * FROM replays ORDER BY start_time DESC LIMIT ? OFFSET ?";
        int offset = (page - 1) * pageSize;

        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

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
        String sql = "SELECT * FROM replays WHERE recorded_player = ? ORDER BY start_time DESC";

        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

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

        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    @Override
    public int deleteOldReplays(int daysOld) throws SQLException {
        String sql = "DELETE FROM replays WHERE start_time < ?";
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L);

        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, cutoffTime);
            return pstmt.executeUpdate();
        }
    }

    @Override
    public int getTotalCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM replays";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    @Override
    public void close() throws SQLException {
        // dbManager kapatma işlemi ana class'ta yapılıyor
    }

    private Replay mapResultSetToReplay(ResultSet rs) throws SQLException {
        // Constructor 4 (11 parametre) kullan - SQLite sütunlarıyla doğru eşleşir
        return new Replay(
                rs.getInt("id"),
                rs.getInt("report_id"),
                rs.getString("recorded_player"),
                "",  // recordedPlayerUuid - SQLite şemasında yok
                rs.getString("world_name"),
                rs.getLong("start_time"),
                rs.getLong("end_time"),
                rs.getInt("duration"),
                rs.getInt("action_count"),
                rs.getBytes("data"),
                rs.getBoolean("compressed")
        );
    }
}