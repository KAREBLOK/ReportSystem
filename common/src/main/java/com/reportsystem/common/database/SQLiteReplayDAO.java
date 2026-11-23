package com.reportsystem.common.database;

import com.reportsystem.common.models.Replay;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

// NOT: SQLite ve MySQL replay semalari uyumsuz. Veritabani tipi degistirmek veri kaybina neden olur.
public class SQLiteReplayDAO implements ReplayDAO {

    private static final Logger LOG = Logger.getLogger("ReportSystem");

    private final SQLiteDatabaseManager dbManager;

    public SQLiteReplayDAO(SQLiteDatabaseManager dbManager) {
        this.dbManager = dbManager;
        migrateCreatedAt();
    }

    /**
     * created_at sütunu (MySQL ile aynı temizlik davranışı): crash-recovery ile kurtarılan
     * replay'lerin yaşı kurtarma anından sayılır. Eski kurulumlar: ALTER try/catch +
     * eski satırlar start_time ile doldurulur. Hata eklenti açılışını bozmaz.
     */
    private void migrateCreatedAt() {
        try {
            Connection conn = dbManager.getConnection();
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("ALTER TABLE replays ADD COLUMN created_at INTEGER DEFAULT NULL");
                } catch (SQLException e) {
                    // Kolon zaten varsa ignore
                }
                stmt.executeUpdate("UPDATE replays SET created_at = start_time WHERE created_at IS NULL");
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[SQLiteReplayDAO] created_at migration failed", e);
        }
    }

    @Override
    public void saveReplay(Replay replay) throws SQLException {
        String sql = "INSERT INTO replays(report_id, recorded_player, world_name, start_time, " +
                "end_time, duration, action_count, data, compressed, created_at) VALUES(?,?,?,?,?,?,?,?,?,?)";

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
            // createdAt 0 ise (beklenmez) start_time'a düş — temizlik hemen silmesin
            long createdAt = replay.getCreatedAt() > 0 ? replay.getCreatedAt() : replay.getStartTime();
            pstmt.setLong(10, createdAt);

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
        // MySQL ile aynı: yaş created_at'ten (kurtarılan replay'ler kurtarma anından sayılır);
        // created_at NULL kalmış satır (migrasyon başarısızsa) start_time'a düşer
        String sql = "DELETE FROM replays WHERE COALESCE(created_at, start_time) < ?";
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
        Replay replay = new Replay(
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
        // created_at (eski şemada yoksa/NULL ise constructor'ın start_time değeri kalır)
        try {
            long createdAt = rs.getLong("created_at");
            if (!rs.wasNull() && createdAt > 0) {
                replay.setCreatedAt(createdAt);
            }
        } catch (SQLException e) {
            // Eski tablolarda bu kolon olmayabilir
        }
        return replay;
    }
}