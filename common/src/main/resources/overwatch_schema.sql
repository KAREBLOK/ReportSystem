-- Overwatch System Database Schema
-- BungeeCord Compatible (cross-server)

-- Overwatch NPCs (lokasyonlar - her sunucu için)
CREATE TABLE IF NOT EXISTS overwatch_npcs (
    id VARCHAR(36) PRIMARY KEY,
    server_name VARCHAR(64) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_at BIGINT NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    INDEX idx_server (server_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Overwatch Queue (incelemeyi bekleyen raporlar)
CREATE TABLE IF NOT EXISTS overwatch_queue (
    report_id INT PRIMARY KEY,
    priority INT DEFAULT 0, -- Yüksek priority önce gelir
    added_at BIGINT NOT NULL,
    assigned_to VARCHAR(36) DEFAULT NULL, -- Reviewer UUID (şu an izleyen)
    assigned_at BIGINT DEFAULT NULL,
    assigned_server VARCHAR(64) DEFAULT NULL, -- Hangi sunucuda izleniyor
    status ENUM('PENDING', 'IN_REVIEW', 'COMPLETED', 'SKIPPED') DEFAULT 'PENDING',
    FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE,
    INDEX idx_status (status),
    INDEX idx_assigned (assigned_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Overwatch Reviews (oylamalar)
CREATE TABLE IF NOT EXISTS overwatch_reviews (
    id INT AUTO_INCREMENT PRIMARY KEY,
    report_id INT NOT NULL,
    reviewer_uuid VARCHAR(36) NOT NULL,
    reviewer_name VARCHAR(32) NOT NULL,
    verdict ENUM('GUILTY', 'INNOCENT', 'SKIP') NOT NULL,
    category VARCHAR(64) DEFAULT NULL, -- Combat, Movement, Visual, Other
    subcategory VARCHAR(64) DEFAULT NULL, -- KillAura, Fly, XRay, etc.
    confidence INT DEFAULT 100, -- 0-100 (ne kadar eminsin)
    notes TEXT DEFAULT NULL,
    reviewed_at BIGINT NOT NULL,
    review_duration INT DEFAULT 0, -- Kaç saniye izledi
    server_name VARCHAR(64) NOT NULL,
    FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE,
    INDEX idx_report (report_id),
    INDEX idx_reviewer (reviewer_uuid),
    INDEX idx_verdict (verdict),
    UNIQUE KEY unique_review (report_id, reviewer_uuid) -- Aynı kişi aynı raporu tekrar izleyemez
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Overwatch Stats (reviewer istatistikleri)
CREATE TABLE IF NOT EXISTS overwatch_stats (
    reviewer_uuid VARCHAR(36) PRIMARY KEY,
    reviewer_name VARCHAR(32) NOT NULL,
    total_reviews INT DEFAULT 0,
    guilty_verdicts INT DEFAULT 0,
    innocent_verdicts INT DEFAULT 0,
    skipped_verdicts INT DEFAULT 0,
    accuracy DOUBLE DEFAULT 0.0, -- %0-100 (doğruluk oranı)
    xp INT DEFAULT 0,
    level INT DEFAULT 1,
    rank VARCHAR(32) DEFAULT 'BRONZE', -- BRONZE, SILVER, GOLD, DIAMOND
    first_review_at BIGINT DEFAULT NULL,
    last_review_at BIGINT DEFAULT NULL,
    total_review_time INT DEFAULT 0, -- Toplam izleme süresi (saniye)
    INDEX idx_accuracy (accuracy),
    INDEX idx_xp (xp),
    INDEX idx_rank (rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Overwatch Actions (admin actions & system logs)
CREATE TABLE IF NOT EXISTS overwatch_actions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    report_id INT NOT NULL,
    action_type ENUM('AUTO_BAN', 'AUTO_MUTE', 'STAFF_REVIEW', 'INSUFFICIENT_EVIDENCE') NOT NULL,
    reason TEXT DEFAULT NULL,
    consensus_percentage DOUBLE DEFAULT 0.0,
    total_reviewers INT DEFAULT 0,
    guilty_count INT DEFAULT 0,
    executed_at BIGINT NOT NULL,
    executed_by VARCHAR(64) DEFAULT 'SYSTEM',
    FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE,
    INDEX idx_report (report_id),
    INDEX idx_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
