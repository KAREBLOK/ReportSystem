package com.reportsystem.common.service;

import com.reportsystem.common.database.ReportDAO;
import com.reportsystem.common.models.Report;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReportService {

    private final ReportDAO reportDAO;
    private static final Logger logger = Logger.getLogger(ReportService.class.getName());

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    /**
     * Yeni bir rapor oluşturur
     * @return Report ID, -1 if error, -2 if limit reached
     */
    public CompletableFuture<Integer> createReport(String reporterName, String reporterUuid,
                                                   String reportedName, String reportedUuid,
                                                   String reason, String serverName, int maxReportsPerPlayer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Limit kontrolü (eğer limit aktifse)
                if (maxReportsPerPlayer > 0) {
                    int existingReports = reportDAO.countReportsByReporterAndReported(reporterName, reportedName);
                    if (existingReports >= maxReportsPerPlayer) {
                        logger.warning("Report limit reached: " + reporterName + " -> " + reportedName +
                                     " (" + existingReports + "/" + maxReportsPerPlayer + ")");
                        return -2; // Limit aşıldı
                    }
                }

                // UUID'yi parse et
                UUID reporterUUID = UUID.fromString(reporterUuid);

                // DAO'nun beklediği formatta çağır
                Report report = reportDAO.createReport(reportedName, reporterName, reporterUUID, reason);

                if (report != null) {
                    // Server bilgisini güncelle
                    if (serverName != null && !serverName.isEmpty()) {
                        reportDAO.updateReportServer(report.getId(), serverName);
                    }
                    return report.getId();
                }
                return -1;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Rapor oluşturulurken hata", e);
                return -1;
            }
        });
    }

    /**
     * Server bilgisi ile rapor oluşturur (BungeeCord için)
     * @return Report object, null if error or limit reached
     */
    public Report createReportWithServer(String reportedPlayerName, String reporterName,
                                         UUID reporterUUID, String reason, String serverName, int maxReportsPerPlayer) {
        try {
            // Limit kontrolü (eğer limit aktifse)
            if (maxReportsPerPlayer > 0) {
                int existingReports = reportDAO.countReportsByReporterAndReported(reporterName, reportedPlayerName);
                if (existingReports >= maxReportsPerPlayer) {
                    logger.warning("Report limit reached: " + reporterName + " -> " + reportedPlayerName +
                                 " (" + existingReports + "/" + maxReportsPerPlayer + ")");
                    return null; // Limit aşıldı
                }
            }

            Report report = reportDAO.createReport(reportedPlayerName, reporterName, reporterUUID, reason);
            if (report != null) {
                if (serverName != null && !serverName.isEmpty()) {
                    reportDAO.updateReportServer(report.getId(), serverName);
                    report.setServerName(serverName); // Report nesnesini de güncelle
                }
                return report;
            }
            return null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Rapor oluşturulurken hata", e);
            return null;
        }
    }

    /**
     * Rapor durumunu günceller
     */
    public boolean updateReportStatus(int reportId, String status) {
        try {
            return reportDAO.updateReportStatus(reportId, status);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Rapor durumu güncellenirken hata", e);
            return false;
        }
    }

    /**
     * Rapor ceza durumunu günceller
     */
    public boolean updateReportPunishment(int reportId, String punishmentType) {
        try {
            return reportDAO.updatePunishmentStatus(reportId, punishmentType);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Rapor ceza durumu güncellenirken hata", e);
            return false;
        }
    }

    /**
     * ID ile rapor getirir
     */
    public Report getReportById(int id) {
        try {
            return reportDAO.getReport(id);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Rapor getirilirken hata", e);
            return null;
        }
    }

    /**
     * ID ile rapor getirir (Optional olarak)
     */
    public Optional<Report> getReportByIdOptional(int id) throws SQLException {
        Report report = reportDAO.getReport(id);
        return Optional.ofNullable(report);
    }

    /**
     * ID ile rapor getirir (eski metod adı uyumluluk için)
     */
    public Report getReport(int id) {
        return getReportById(id);
    }

    /**
     * Sayfalı raporları getirir
     */
    public List<Report> getReports(int page, int perPage) {
        try {
            return reportDAO.getReports(page, perPage);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Raporlar getirilirken hata", e);
            return List.of();
        }
    }

    /**
     * Oyuncunun raporlarını getirir
     */
    public List<Report> getReportsByPlayer(String playerName) {
        try {
            return reportDAO.getReportsByPlayer(playerName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Oyuncu raporları getirilirken hata", e);
            return List.of();
        }
    }

    /**
     * Belirli bir oyuncu hakkındaki raporları getirir
     */
    public List<Report> getReportsAboutPlayer(String playerName) throws SQLException {
        return reportDAO.getReportsByPlayer(playerName);
    }

    /**
     * Belirli bir sunucudaki raporları getirir
     */
    public List<Report> getReportsByServer(String serverName) throws SQLException {
        return reportDAO.getReportsByServer(serverName);
    }

    /**
     * Belirli bir duruma göre raporları getirir
     */
    public List<Report> getReportsByStatus(String status) throws SQLException {
        return reportDAO.getReportsByStatus(status);
    }

    /**
     * Tüm raporları sayfalı olarak getirir
     */
    public List<Report> getAllReports(int offset, int limit) throws SQLException {
        return reportDAO.getReports((offset / limit) + 1, limit);
    }

    /**
     * Belirli bir tarih aralığındaki raporları getirir
     */
    public List<Report> getReportsByDateRange(Date startDate, Date endDate) throws SQLException {
        return reportDAO.getReportsByDateRange(startDate.getTime(), endDate.getTime());
    }

    /**
     * Bekleyen raporları getirir
     */
    public List<Report> getPendingReports() {
        try {
            return reportDAO.getReportsByStatus("PENDING");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Bekleyen raporlar getirilirken hata", e);
            return List.of();
        }
    }

    /**
     * Toplam rapor sayısını döndürür
     */
    public int getTotalReports() throws SQLException {
        return reportDAO.getReportCount();
    }

    /**
     * Bekleyen rapor sayısını döndürür
     */
    public int getPendingReportsCount() throws SQLException {
        return reportDAO.getPendingCount();
    }

    /**
     * Eski raporları otomatik olarak kapatır (durumu CLOSED yapar)
     */
    public void autoCloseOldReports(int days) {
        try {
            long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
            int closed = reportDAO.closeOldReports(cutoffTime);
            if (closed > 0) {
                logger.info(closed + " eski rapor otomatik olarak kapatıldı (CLOSED durumuna getirildi).");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Eski raporlar kapatılırken hata", e);
        }
    }

    /**
     * Tüm raporları getirir
     */
    public List<Report> getAllReports() {
        try {
            return reportDAO.getAllReports();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Tüm raporlar getirilirken hata", e);
            return List.of();
        }
    }

    /**
     * Rapor siler
     */
    public boolean deleteReport(int id) {
        try {
            return reportDAO.deleteReport(id);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Rapor silinirken hata", e);
            return false;
        }
    }

    /**
     * Toplam rapor sayısını döndürür
     */
    public int getReportCount() {
        try {
            return reportDAO.getReportCount();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Rapor sayısı alınırken hata", e);
            return 0;
        }
    }

    /**
     * Belirli bir oyuncunun kaç kez raporlandığını döndürür
     */
    public int getReportCount(String playerName) {
        try {
            return reportDAO.getTotalReportsForPlayer(playerName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Rapor sayısı alınırken hata", e);
            return 0;
        }
    }

    /**
     * Son X raporu getirir
     */
    public List<Report> getRecentReports(int limit) {
        try {
            return reportDAO.getReports(1, limit);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Son raporlar getirilirken hata", e);
            return List.of();
        }
    }

    /**
     * Raporu günceller
     */
    public void updateReport(Report report) throws SQLException {
        reportDAO.updateReportStatus(report.getId(), report.getStatus());
        if (report.isPunished()) {
            reportDAO.updatePunishmentStatus(report.getId(), report.getPunishmentType());
        }
    }

    /**
     * Raporcu'nun henüz bildirim almadığı kabul edilmiş raporları getirir
     */
    public List<Report> getUnnotifiedReportsForReporter(String reporterUuid) {
        try {
            return reportDAO.getUnnotifiedReportsForReporter(reporterUuid);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Bildirilmemiş raporlar getirilirken hata", e);
            return List.of();
        }
    }

    /**
     * Raporu "raporcu bildirildi" olarak işaretler
     */
    public void markReporterNotified(int reportId) {
        try {
            reportDAO.markReporterNotified(reportId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Rapor bildirim durumu güncellenirken hata", e);
        }
    }

}