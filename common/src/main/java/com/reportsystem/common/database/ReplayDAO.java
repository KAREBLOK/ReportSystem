package com.reportsystem.common.database;

import com.reportsystem.common.models.Replay;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ReplayDAO {

    /**
     * Yeni bir replay kaydeder
     */
    void saveReplay(Replay replay) throws SQLException;

    /**
     * Belirli bir rapor için replay'i getirir
     */
    Optional<Replay> getReplayByReportId(int reportId) throws SQLException;

    /**
     * ID'ye göre replay getirir
     */
    Optional<Replay> getReplayById(int id) throws SQLException;

    /**
     * Tüm replay'leri listeler (sayfalama ile)
     */
    List<Replay> getReplays(int page, int pageSize) throws SQLException;

    /**
     * Belirli bir oyuncunun tüm replay'lerini getirir
     */
    List<Replay> getReplaysByPlayer(String playerName) throws SQLException;

    /**
     * Replay'i siler
     */
    void deleteReplay(int id) throws SQLException;

    /**
     * Eski replay'leri temizler (gün cinsinden)
     */
    int deleteOldReplays(int daysOld) throws SQLException;

    /**
     * Toplam replay sayısını döndürür
     */
    int getTotalCount() throws SQLException;

    /**
     * Bağlantıyı kapatır
     */
    void close() throws SQLException;
}