package com.reportsystem.spigot.trust;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hesap Durumu Seviyesi (Trust Level) Sistemi
 *
 * İhlal puanı zamana göre düşer (half-life decay):
 * - Her ceza türü farklı puan verir (BAN=40, MUTE=15, KICK=10, WARN=5)
 * - Eski ihlallerin etkisi zamanla azalır (varsayılan yarı ömür: 30 gün)
 * - İyi davranış (ihlal almamak) puanı doğal olarak düşürür
 *
 * Yeni tablo/migration gerektirmez — mevcut reports tablosundan hesaplanır.
 */
public class TrustLevelManager {

    public enum TrustLevel {
        EXCELLENT("EXCELLENT", "&a", 0),
        GOOD("GOOD", "&2", 10),
        MODERATE("MODERATE", "&e", 30),
        POOR("POOR", "&6", 60),
        CRITICAL("CRITICAL", "&c", 90);

        private final String name;
        private final String color;
        private final int minPoints;

        TrustLevel(String name, String color, int minPoints) {
            this.name = name;
            this.color = color;
            this.minPoints = minPoints;
        }

        public String getName() { return name; }
        public String getColor() { return color; }
        public int getMinPoints() { return minPoints; }
    }

    private final ReportSystemSpigot plugin;
    private final Map<UUID, CachedTrust> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 5 * 60 * 1000L; // 5 dakika

    public TrustLevelManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    /**
     * Oyuncunun ihlal puanını hesapla (decay uygulanmış)
     */
    public double getViolationPoints(UUID playerUuid, String playerName) {
        // Cache kontrolü
        CachedTrust cached = cache.get(playerUuid);
        if (cached != null && !cached.isExpired()) {
            return cached.points;
        }

        // Mevcut reports tablosundan hesapla
        double points = calculatePoints(playerName);

        cache.put(playerUuid, new CachedTrust(points, System.currentTimeMillis()));
        return points;
    }

    /**
     * Oyuncunun trust level'ını getir
     */
    public TrustLevel getTrustLevel(UUID playerUuid, String playerName) {
        double points = getViolationPoints(playerUuid, playerName);
        return getTrustLevelFromPoints(points);
    }

    /**
     * Puandan trust level hesapla
     */
    public TrustLevel getTrustLevelFromPoints(double points) {
        if (points >= 90) return TrustLevel.CRITICAL;
        if (points >= 60) return TrustLevel.POOR;
        if (points >= 30) return TrustLevel.MODERATE;
        if (points >= 10) return TrustLevel.GOOD;
        return TrustLevel.EXCELLENT;
    }

    /**
     * Trust level'ın lokalize adını getir
     */
    public String getLocalizedLevelName(TrustLevel level) {
        String path = "trust-level.levels." + level.getName().toLowerCase();
        return plugin.getMessageManager().getMessage(path);
    }

    /**
     * ACCEPTED raporlardan ağırlıklı ihlal puanı hesapla
     * Her raporun puanı zamanla azalır: points * (0.5 ^ (days / halfLife))
     */
    private double calculatePoints(String playerName) {
        try {
            List<Report> reports = plugin.getReportService().getReportsByPlayer(playerName);
            if (reports == null || reports.isEmpty()) return 0;

            double halfLifeDays = plugin.getConfig().getDouble("trust-level.decay.half-life-days", 30.0);
            long now = System.currentTimeMillis();
            double totalPoints = 0;

            for (Report report : reports) {
                if (!"ACCEPTED".equalsIgnoreCase(report.getStatus())) continue;
                if (!report.isPunished()) continue;

                double basePoints = getBasePoints(report.getPunishmentType());
                long ageMs = now - report.getTimestamp();
                double ageDays = ageMs / (24.0 * 60 * 60 * 1000);

                // Half-life decay: basePoints * 0.5^(ageDays/halfLife)
                double decayFactor = Math.pow(0.5, ageDays / halfLifeDays);
                totalPoints += basePoints * decayFactor;
            }

            return totalPoints;
        } catch (Exception e) {
            plugin.getLogger().warning("Trust level hesaplanamadı: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Ceza türüne göre baz ihlal puanı
     */
    private double getBasePoints(String punishmentType) {
        if (punishmentType == null) return 5;
        String key = "trust-level.points." + punishmentType.toLowerCase();
        return plugin.getConfig().getDouble(key, getDefaultPoints(punishmentType));
    }

    private double getDefaultPoints(String type) {
        if (type == null) return 5;
        switch (type.toUpperCase()) {
            case "BAN": return 40;
            case "MUTE": return 15;
            case "KICK": return 10;
            case "WARN": return 5;
            default: return 5;
        }
    }

    /**
     * Cache'i belirli oyuncu için temizle (ceza verildiğinde çağrılır)
     */
    public void invalidateCache(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    /**
     * Tüm cache'i temizle
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Trust level formatını renkli string olarak döndür
     */
    public String getFormattedTrustLevel(UUID playerUuid, String playerName) {
        TrustLevel level = getTrustLevel(playerUuid, playerName);
        String localizedName = getLocalizedLevelName(level);
        return level.getColor() + localizedName;
    }

    /**
     * Trust level detay string'i (GUI/komut için)
     */
    public String getDetailedTrustInfo(UUID playerUuid, String playerName) {
        double points = getViolationPoints(playerUuid, playerName);
        TrustLevel level = getTrustLevelFromPoints(points);
        String localizedName = getLocalizedLevelName(level);
        return level.getColor() + localizedName + " &7(" + String.format("%.1f", points) + " puan)";
    }

    private static class CachedTrust {
        final double points;
        final long cachedAt;

        CachedTrust(double points, long cachedAt) {
            this.points = points;
            this.cachedAt = cachedAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL;
        }
    }
}
