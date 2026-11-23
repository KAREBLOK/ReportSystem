package com.reportsystem.common.database;

import com.reportsystem.common.models.Report;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ReportDAO {
    Report createReport(String reportedPlayerName, String reporterName, UUID reporterUUID, String reason);
    Report getReport(int id);
    List<Report> getReports(int page, int perPage);
    List<Report> getAllReports();
    List<Report> getReportsByPlayer(String playerName);
    List<Report> getReportsByReporter(String reporterName);
    int getReportCount();
    boolean updateReportStatus(int id, String status);

    /**
     * Atomik koşullu durum geçişi: yalnız rapor hâlâ {@code expectedStatus} durumundaysa
     * {@code newStatus}'a geçirir ({@code UPDATE ... WHERE id = ? AND status = ?}).
     * İki yetkilinin aynı raporu aynı anda işlemesini (çift ceza / üzerine yazma) engeller.
     *
     * @return Etkilenen satır sayısı (0/1). 0 = rapor beklenen durumda değil (başkası işlemiş).
     * @throws SQLException DB hatası yutulmaz, çağırana iletilir.
     */
    int transitionStatus(int id, String expectedStatus, String newStatus) throws SQLException;
    boolean updateReportServer(int id, String serverName);
    boolean updatePunishmentStatus(int reportId, String punishmentType) throws SQLException;

    /**
     * Raporu çözen yetkiliyi ve çözülme zamanını kalıcı yazar (H-14a). Bloklayan DB çağrısı.
     */
    boolean setResolution(int id, String resolvedBy, long resolvedAt) throws SQLException;
    boolean deleteReport(int id);
    void deleteOldReports(int days);

    // Yeni eklenecek metotlar
    int getTotalCount() throws SQLException;
    int getPendingCount() throws SQLException;
    int getAcceptedCount() throws SQLException;
    int getRejectedCount() throws SQLException;
    int closeOldReports(long cutoffTime) throws SQLException;
    void close() throws SQLException;

    // Filtrelenmiş sorgular
    List<Report> getReportsByStatus(String status);
    List<Report> getReportsByServer(String serverName);
    List<Report> getReportsByDateRange(long startTime, long endTime);

    // Spam koruması için
    int countReportsByReporterAndReported(String reporterName, String reportedName);

    // Oyuncunun toplam rapor sayısı
    int getTotalReportsForPlayer(String playerName);

    // Raporcu geri bildirim sistemi
    List<Report> getUnnotifiedReportsForReporter(String reporterUuid) throws SQLException;
    void markReporterNotified(int reportId) throws SQLException;
}