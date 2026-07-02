package com.reportsystem.common.database;

import com.reportsystem.common.models.overwatch.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OverwatchDAO {

    private final Database database;
    private final boolean isMySQL;

    public OverwatchDAO(Database database) {
        this.database = database;
        this.isMySQL = !(database instanceof SQLiteDatabase);
    }

    /**
     * Create all Overwatch tables if they don't exist
     */
    public void createTables() throws SQLException {
        if (isMySQL) {
            createTablesMySQL();
        } else {
            createTablesSQLite();
        }
    }

    private void createTablesMySQL() throws SQLException {
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create overwatch_npcs table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_npcs (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "server_name VARCHAR(64) NOT NULL, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "x DOUBLE NOT NULL, " +
                    "y DOUBLE NOT NULL, " +
                    "z DOUBLE NOT NULL, " +
                    "yaw FLOAT NOT NULL, " +
                    "pitch FLOAT NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "created_by VARCHAR(36) NOT NULL, " +
                    "display_name VARCHAR(64) DEFAULT NULL, " +
                    "INDEX idx_server (server_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // Create overwatch_queue table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_queue (" +
                    "report_id INT PRIMARY KEY, " +
                    "priority INT DEFAULT 0, " +
                    "added_at BIGINT NOT NULL, " +
                    "assigned_to VARCHAR(36) DEFAULT NULL, " +
                    "assigned_at BIGINT DEFAULT NULL, " +
                    "assigned_server VARCHAR(64) DEFAULT NULL, " +
                    "status VARCHAR(20) DEFAULT 'PENDING', " +
                    "INDEX idx_status (status), " +
                    "INDEX idx_assigned (assigned_to)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // Create overwatch_reviews table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_reviews (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "report_id INT NOT NULL, " +
                    "reviewer_uuid VARCHAR(36) NOT NULL, " +
                    "reviewer_name VARCHAR(16) NOT NULL, " +
                    "verdict VARCHAR(20) NOT NULL, " +
                    "category VARCHAR(50), " +
                    "subcategory VARCHAR(50), " +
                    "confidence INT DEFAULT 100, " +
                    "notes TEXT, " +
                    "reviewed_at BIGINT NOT NULL, " +
                    "review_duration INT NOT NULL, " +
                    "server_name VARCHAR(64) NOT NULL, " +
                    "INDEX idx_report (report_id), " +
                    "INDEX idx_reviewer (reviewer_uuid)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // Create overwatch_stats table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_stats (" +
                    "reviewer_uuid VARCHAR(36) PRIMARY KEY, " +
                    "reviewer_name VARCHAR(16) NOT NULL, " +
                    "total_reviews INT DEFAULT 0, " +
                    "guilty_verdicts INT DEFAULT 0, " +
                    "innocent_verdicts INT DEFAULT 0, " +
                    "skipped_verdicts INT DEFAULT 0, " +
                    "accuracy DOUBLE DEFAULT 100.0, " +
                    "xp INT DEFAULT 0, " +
                    "level INT DEFAULT 1, " +
                    "rank VARCHAR(20) DEFAULT 'BRONZE', " +
                    "first_review_at BIGINT DEFAULT 0, " +
                    "last_review_at BIGINT DEFAULT 0, " +
                    "total_review_time INT DEFAULT 0, " +
                    "INDEX idx_xp (xp)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // Create overwatch_actions table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_actions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "report_id INT NOT NULL, " +
                    "action_type VARCHAR(50) NOT NULL, " +
                    "reason TEXT NOT NULL, " +
                    "consensus_percentage DOUBLE NOT NULL, " +
                    "total_reviewers INT NOT NULL, " +
                    "guilty_count INT NOT NULL, " +
                    "executed_at BIGINT NOT NULL, " +
                    "executed_by VARCHAR(64) NOT NULL, " +
                    "INDEX idx_report (report_id), " +
                    "INDEX idx_type (action_type)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
    }

    private void createTablesSQLite() throws SQLException {
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create overwatch_npcs table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_npcs (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "server_name VARCHAR(64) NOT NULL, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "x DOUBLE NOT NULL, " +
                    "y DOUBLE NOT NULL, " +
                    "z DOUBLE NOT NULL, " +
                    "yaw FLOAT NOT NULL, " +
                    "pitch FLOAT NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "created_by VARCHAR(36) NOT NULL, " +
                    "display_name VARCHAR(64) DEFAULT NULL" +
                    ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_npcs_server ON overwatch_npcs (server_name)");

            // Create overwatch_queue table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_queue (" +
                    "report_id INTEGER PRIMARY KEY, " +
                    "priority INTEGER DEFAULT 0, " +
                    "added_at BIGINT NOT NULL, " +
                    "assigned_to VARCHAR(36) DEFAULT NULL, " +
                    "assigned_at BIGINT DEFAULT NULL, " +
                    "assigned_server VARCHAR(64) DEFAULT NULL, " +
                    "status VARCHAR(20) DEFAULT 'PENDING'" +
                    ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_queue_status ON overwatch_queue (status)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_queue_assigned ON overwatch_queue (assigned_to)");

            // Create overwatch_reviews table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_reviews (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "report_id INTEGER NOT NULL, " +
                    "reviewer_uuid VARCHAR(36) NOT NULL, " +
                    "reviewer_name VARCHAR(16) NOT NULL, " +
                    "verdict VARCHAR(20) NOT NULL, " +
                    "category VARCHAR(50), " +
                    "subcategory VARCHAR(50), " +
                    "confidence INTEGER DEFAULT 100, " +
                    "notes TEXT, " +
                    "reviewed_at BIGINT NOT NULL, " +
                    "review_duration INTEGER NOT NULL, " +
                    "server_name VARCHAR(64) NOT NULL" +
                    ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reviews_report ON overwatch_reviews (report_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reviews_reviewer ON overwatch_reviews (reviewer_uuid)");

            // Create overwatch_stats table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_stats (" +
                    "reviewer_uuid VARCHAR(36) PRIMARY KEY, " +
                    "reviewer_name VARCHAR(16) NOT NULL, " +
                    "total_reviews INTEGER DEFAULT 0, " +
                    "guilty_verdicts INTEGER DEFAULT 0, " +
                    "innocent_verdicts INTEGER DEFAULT 0, " +
                    "skipped_verdicts INTEGER DEFAULT 0, " +
                    "accuracy DOUBLE DEFAULT 100.0, " +
                    "xp INTEGER DEFAULT 0, " +
                    "level INTEGER DEFAULT 1, " +
                    "rank VARCHAR(20) DEFAULT 'BRONZE', " +
                    "first_review_at BIGINT DEFAULT 0, " +
                    "last_review_at BIGINT DEFAULT 0, " +
                    "total_review_time INTEGER DEFAULT 0" +
                    ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_xp ON overwatch_stats (xp)");

            // Create overwatch_actions table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS overwatch_actions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "report_id INTEGER NOT NULL, " +
                    "action_type VARCHAR(50) NOT NULL, " +
                    "reason TEXT NOT NULL, " +
                    "consensus_percentage DOUBLE NOT NULL, " +
                    "total_reviewers INTEGER NOT NULL, " +
                    "guilty_count INTEGER NOT NULL, " +
                    "executed_at BIGINT NOT NULL, " +
                    "executed_by VARCHAR(64) NOT NULL" +
                    ")");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_actions_report ON overwatch_actions (report_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_actions_type ON overwatch_actions (action_type)");
        }
    }

    /**
     * Migrate existing tables to add new columns
     */
    public void migrateTables() throws SQLException {
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if display_name column exists in overwatch_npcs table
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "overwatch_npcs", "display_name")) {
                if (!rs.next()) {
                    stmt.executeUpdate("ALTER TABLE overwatch_npcs ADD COLUMN display_name VARCHAR(64) DEFAULT NULL");
                    System.out.println("[OVERWATCH-DB] Added display_name column to overwatch_npcs table");
                }
            }

            // Add skin columns to overwatch_npcs
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "overwatch_npcs", "skin_texture")) {
                if (!rs.next()) {
                    stmt.executeUpdate("ALTER TABLE overwatch_npcs ADD COLUMN skin_texture TEXT DEFAULT NULL");
                    stmt.executeUpdate("ALTER TABLE overwatch_npcs ADD COLUMN skin_signature TEXT DEFAULT NULL");
                    System.out.println("[OVERWATCH-DB] Added skin columns to overwatch_npcs table");
                }
            }

            // Add correct_verdicts column to overwatch_stats
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "overwatch_stats", "correct_verdicts")) {
                if (!rs.next()) {
                    if (isMySQL) {
                        stmt.executeUpdate("ALTER TABLE overwatch_stats ADD COLUMN correct_verdicts INT DEFAULT 0");
                    } else {
                        stmt.executeUpdate("ALTER TABLE overwatch_stats ADD COLUMN correct_verdicts INTEGER DEFAULT 0");
                    }
                    System.out.println("[OVERWATCH-DB] Added correct_verdicts column to overwatch_stats table");
                }
            }
        }
    }

    // ============= NPC Management =============

    public void createNPC(OverwatchNPC npc) throws SQLException {
        String sql = "INSERT INTO overwatch_npcs (id, server_name, world, x, y, z, yaw, pitch, created_at, created_by, display_name, skin_texture, skin_signature) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, npc.getId());
            stmt.setString(2, npc.getServerName());
            stmt.setString(3, npc.getWorld());
            stmt.setDouble(4, npc.getX());
            stmt.setDouble(5, npc.getY());
            stmt.setDouble(6, npc.getZ());
            stmt.setFloat(7, npc.getYaw());
            stmt.setFloat(8, npc.getPitch());
            stmt.setLong(9, npc.getCreatedAt());
            stmt.setString(10, npc.getCreatedBy());
            stmt.setString(11, npc.getDisplayName());
            stmt.setString(12, npc.getSkinTexture());
            stmt.setString(13, npc.getSkinSignature());

            stmt.executeUpdate();
        }
    }

    public void deleteNPC(String id) throws SQLException {
        String sql = "DELETE FROM overwatch_npcs WHERE id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }

    public List<OverwatchNPC> getAllNPCs(String serverName) throws SQLException {
        String sql = "SELECT * FROM overwatch_npcs WHERE server_name = ?";
        List<OverwatchNPC> npcs = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, serverName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    npcs.add(new OverwatchNPC(
                        rs.getString("id"),
                        rs.getString("server_name"),
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch"),
                        rs.getLong("created_at"),
                        rs.getString("created_by"),
                        rs.getString("display_name"),
                        rs.getString("skin_texture"),
                        rs.getString("skin_signature")
                    ));
                }
            }
        }

        return npcs;
    }

    public void updateNPCPosition(String id, String world, double x, double y, double z, float yaw, float pitch) throws SQLException {
        String sql = "UPDATE overwatch_npcs SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setDouble(2, x);
            stmt.setDouble(3, y);
            stmt.setDouble(4, z);
            stmt.setFloat(5, yaw);
            stmt.setFloat(6, pitch);
            stmt.setString(7, id);
            stmt.executeUpdate();
        }
    }

    public void updateNPCSkin(String id, String texture, String signature) throws SQLException {
        String sql = "UPDATE overwatch_npcs SET skin_texture = ?, skin_signature = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, texture);
            stmt.setString(2, signature);
            stmt.setString(3, id);
            stmt.executeUpdate();
        }
    }

    public void updateNPCDisplayName(String id, String displayName) throws SQLException {
        String sql = "UPDATE overwatch_npcs SET display_name = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, displayName);
            stmt.setString(2, id);
            stmt.executeUpdate();
        }
    }

    public void updateNPCRotation(String id, float yaw, float pitch) throws SQLException {
        String sql = "UPDATE overwatch_npcs SET yaw = ?, pitch = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setFloat(1, yaw);
            stmt.setFloat(2, pitch);
            stmt.setString(3, id);
            stmt.executeUpdate();
        }
    }

    // ============= Queue Management =============

    public void addToQueue(int reportId, int priority) throws SQLException {
        String sql;
        if (isMySQL) {
            sql = "INSERT INTO overwatch_queue (report_id, priority, added_at, status) " +
                  "VALUES (?, ?, ?, 'PENDING') " +
                  "ON DUPLICATE KEY UPDATE priority = VALUES(priority)";
        } else {
            sql = "INSERT OR REPLACE INTO overwatch_queue (report_id, priority, added_at, status) " +
                  "VALUES (?, ?, ?, 'PENDING')";
        }

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reportId);
            stmt.setInt(2, priority);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public void removeFromQueue(int reportId) throws SQLException {
        String sql = "DELETE FROM overwatch_queue WHERE report_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reportId);
            stmt.executeUpdate();
        }
    }

    public void updateQueueStatus(int reportId, String status) throws SQLException {
        String sql = "UPDATE overwatch_queue SET status = ? WHERE report_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setInt(2, reportId);
            stmt.executeUpdate();
        }
    }

    public Optional<OverwatchQueueItem> getNextQueueItem(UUID reviewerUUID) throws SQLException {
        // Get next pending item that this reviewer hasn't reviewed yet
        String sql = "SELECT oq.* FROM overwatch_queue oq " +
                     "LEFT JOIN overwatch_reviews orw ON oq.report_id = orw.report_id AND orw.reviewer_uuid = ? " +
                     "WHERE oq.status = 'PENDING' AND orw.id IS NULL " +
                     "ORDER BY oq.priority DESC, oq.added_at ASC LIMIT 1";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reviewerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new OverwatchQueueItem(
                        rs.getInt("report_id"),
                        rs.getInt("priority"),
                        rs.getLong("added_at"),
                        rs.getString("assigned_to"),
                        rs.getLong("assigned_at"),
                        rs.getString("assigned_server"),
                        rs.getString("status")
                    ));
                }
            }
        }

        return Optional.empty();
    }

    public boolean assignReportToReviewer(int reportId, UUID reviewerUUID, String serverName) throws SQLException {
        String sql = "UPDATE overwatch_queue SET assigned_to = ?, assigned_at = ?, assigned_server = ?, status = 'IN_REVIEW' " +
                     "WHERE report_id = ? AND status = 'PENDING'";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reviewerUUID.toString());
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, serverName);
            stmt.setInt(4, reportId);
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public void completeQueueItem(int reportId) throws SQLException {
        String sql = "UPDATE overwatch_queue SET status = 'COMPLETED' WHERE report_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reportId);
            stmt.executeUpdate();
        }
    }

    /**
     * Unassign a report and put it back to PENDING status
     * Used when reviewer closes verdict GUI without submitting
     */
    public void unassignReport(int reportId) throws SQLException {
        String sql = "UPDATE overwatch_queue SET assigned_to = NULL, assigned_at = NULL, " +
                     "assigned_server = NULL, status = 'PENDING' WHERE report_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reportId);
            stmt.executeUpdate();
        }
    }

    public int getPendingQueueCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM overwatch_queue WHERE status = 'PENDING'";

        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }

    // ============= Review Management =============

    public void submitReview(OverwatchReview review) throws SQLException {
        String sql = "INSERT INTO overwatch_reviews (report_id, reviewer_uuid, reviewer_name, verdict, category, " +
                     "subcategory, confidence, notes, reviewed_at, review_duration, server_name) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, review.getReportId());
            stmt.setString(2, review.getReviewerUUID());
            stmt.setString(3, review.getReviewerName());
            stmt.setString(4, review.getVerdict());
            stmt.setString(5, review.getCategory());
            stmt.setString(6, review.getSubcategory());
            stmt.setInt(7, review.getConfidence());
            stmt.setString(8, review.getNotes());
            stmt.setLong(9, review.getReviewedAt());
            stmt.setInt(10, review.getReviewDuration());
            stmt.setString(11, review.getServerName());

            stmt.executeUpdate();
        }
    }

    public List<OverwatchReview> getReviewsForReport(int reportId) throws SQLException {
        String sql = "SELECT * FROM overwatch_reviews WHERE report_id = ?";
        List<OverwatchReview> reviews = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reportId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reviews.add(new OverwatchReview(
                        rs.getInt("id"),
                        rs.getInt("report_id"),
                        rs.getString("reviewer_uuid"),
                        rs.getString("reviewer_name"),
                        rs.getString("verdict"),
                        rs.getString("category"),
                        rs.getString("subcategory"),
                        rs.getInt("confidence"),
                        rs.getString("notes"),
                        rs.getLong("reviewed_at"),
                        rs.getInt("review_duration"),
                        rs.getString("server_name")
                    ));
                }
            }
        }

        return reviews;
    }

    public int getReviewCount(int reportId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM overwatch_reviews WHERE report_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reportId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    // ============= Stats Management =============

    public void createOrUpdateStats(OverwatchStats stats) throws SQLException {
        String sql;
        if (isMySQL) {
            sql = "INSERT INTO overwatch_stats (reviewer_uuid, reviewer_name, total_reviews, guilty_verdicts, " +
                  "innocent_verdicts, skipped_verdicts, accuracy, xp, level, rank, first_review_at, last_review_at, total_review_time, correct_verdicts) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE " +
                  "reviewer_name = VALUES(reviewer_name), " +
                  "total_reviews = VALUES(total_reviews), " +
                  "guilty_verdicts = VALUES(guilty_verdicts), " +
                  "innocent_verdicts = VALUES(innocent_verdicts), " +
                  "skipped_verdicts = VALUES(skipped_verdicts), " +
                  "accuracy = VALUES(accuracy), " +
                  "xp = VALUES(xp), " +
                  "level = VALUES(level), " +
                  "rank = VALUES(rank), " +
                  "last_review_at = VALUES(last_review_at), " +
                  "total_review_time = VALUES(total_review_time), " +
                  "correct_verdicts = VALUES(correct_verdicts)";
        } else {
            sql = "INSERT OR REPLACE INTO overwatch_stats (reviewer_uuid, reviewer_name, total_reviews, guilty_verdicts, " +
                  "innocent_verdicts, skipped_verdicts, accuracy, xp, level, rank, first_review_at, last_review_at, total_review_time, correct_verdicts) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, stats.getReviewerUUID());
            stmt.setString(2, stats.getReviewerName());
            stmt.setInt(3, stats.getTotalReviews());
            stmt.setInt(4, stats.getGuiltyVerdicts());
            stmt.setInt(5, stats.getInnocentVerdicts());
            stmt.setInt(6, stats.getSkippedVerdicts());
            stmt.setDouble(7, stats.getAccuracy());
            stmt.setInt(8, stats.getXp());
            stmt.setInt(9, stats.getLevel());
            stmt.setString(10, stats.getRank());
            stmt.setLong(11, stats.getFirstReviewAt());
            stmt.setLong(12, stats.getLastReviewAt());
            stmt.setInt(13, stats.getTotalReviewTime());
            stmt.setInt(14, stats.getCorrectVerdicts());

            stmt.executeUpdate();
        }
    }

    public Optional<OverwatchStats> getStats(UUID reviewerUUID) throws SQLException {
        return getStatsByUuid(reviewerUUID.toString());
    }

    public Optional<OverwatchStats> getStatsByUuid(String reviewerUuid) throws SQLException {
        String sql = "SELECT * FROM overwatch_stats WHERE reviewer_uuid = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reviewerUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(extractStats(rs));
                }
            }
        }

        return Optional.empty();
    }

    private OverwatchStats extractStats(ResultSet rs) throws SQLException {
        OverwatchStats stats = new OverwatchStats(
            rs.getString("reviewer_uuid"),
            rs.getString("reviewer_name"),
            rs.getInt("total_reviews"),
            rs.getInt("guilty_verdicts"),
            rs.getInt("innocent_verdicts"),
            rs.getInt("skipped_verdicts"),
            rs.getDouble("accuracy"),
            rs.getInt("xp"),
            rs.getInt("level"),
            rs.getString("rank"),
            rs.getLong("first_review_at"),
            rs.getLong("last_review_at"),
            rs.getInt("total_review_time")
        );
        try {
            stats.setCorrectVerdicts(rs.getInt("correct_verdicts"));
        } catch (SQLException e) {
            // Eski tablolarda bu kolon olmayabilir
        }
        return stats;
    }

    public List<OverwatchStats> getTopReviewers(int limit) throws SQLException {
        String sql = "SELECT * FROM overwatch_stats ORDER BY xp DESC, accuracy DESC LIMIT ?";
        List<OverwatchStats> stats = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.add(extractStats(rs));
                }
            }
        }

        return stats;
    }

    // ============= Actions & Logging =============

    public void logAction(OverwatchAction action) throws SQLException {
        String sql = "INSERT INTO overwatch_actions (report_id, action_type, reason, consensus_percentage, " +
                     "total_reviewers, guilty_count, executed_at, executed_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, action.getReportId());
            stmt.setString(2, action.getActionType());
            stmt.setString(3, action.getReason());
            stmt.setDouble(4, action.getConsensusPercentage());
            stmt.setInt(5, action.getTotalReviewers());
            stmt.setInt(6, action.getGuiltyCount());
            stmt.setLong(7, action.getExecutedAt());
            stmt.setString(8, action.getExecutedBy());

            stmt.executeUpdate();
        }
    }

    // ============= Statistics =============

    public int getTotalPunishedCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM overwatch_actions WHERE action_type IN ('AUTO_BAN', 'AUTO_MUTE')";

        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }

    public int getWeeklyReviewedCount() throws SQLException {
        long oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        String sql = "SELECT COUNT(DISTINCT report_id) FROM overwatch_reviews WHERE reviewed_at > ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, oneWeekAgo);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }
}
