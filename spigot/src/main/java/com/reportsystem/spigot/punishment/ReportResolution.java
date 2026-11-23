package com.reportsystem.spigot.punishment;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;

import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Rapor sonuçlandırma yardımcısı (çift ceza / üzerine yazma koruması).
 *
 * Akış: cezadan ÖNCE {@link #claim} ile rapor atomik olarak PENDING -> ACCEPTED/REJECTED
 * talep edilir (DB'de koşullu UPDATE). Yalnız talep başarılıysa ceza uygulanır; ceza
 * uygulanamazsa {@link #revert} ile rapor tekrar PENDING'e alınır. Kabul sonrası DB işleri
 * {@link #finishAsync} ile yapılır.
 *
 * Thread kuralı: DB çağrıları YALNIZ async görevde, Bukkit/oyuncu çağrıları YALNIZ ana thread'de
 * ({@code onMain} geri çağrısı). Ana thread hiçbir zaman beklemez.
 */
public final class ReportResolution {

    public enum ClaimResult { CLAIMED, ALREADY_HANDLED, ERROR }

    private static final String PENDING = Report.Status.PENDING.getValue();

    private ReportResolution() {
    }

    /**
     * Raporu PENDING -> newStatus olarak atomik talep eder. DB async; sonuç ana thread'de
     * {@code onMain}'e verilir. Eklenti bu arada kapanmışsa geri çağrı yapılmaz ve
     * yapılmış talep geri alınır (ceza uygulanamayacağı için rapor PENDING'de kalsın).
     */
    public static void claim(ReportSystemSpigot plugin, int reportId, Report.Status newStatus,
                             Consumer<ClaimResult> onMain) {
        final String target = newStatus.getValue();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ClaimResult result;
            try {
                int rows = plugin.getReportService().transitionStatus(reportId, PENDING, target);
                if (rows > 0) {
                    result = ClaimResult.CLAIMED;
                } else if (rows == 0) {
                    result = ClaimResult.ALREADY_HANDLED;
                } else {
                    result = ClaimResult.ERROR;
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "[ReportResolution] Claim failed for report #" + reportId, t);
                result = ClaimResult.ERROR;
            }

            final ClaimResult finalResult = result;
            if (!plugin.isEnabled()) {
                // THR-17: devre dışı eklentiye runTask atılamaz. Talep edildiyse geri al.
                if (finalResult == ClaimResult.CLAIMED) {
                    revertNow(plugin, reportId, target);
                }
                return;
            }
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        onMain.accept(finalResult);
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.SEVERE,
                                "[ReportResolution] Claim callback error for report #" + reportId, t);
                    }
                });
            } catch (Throwable t) {
                // Kontrol ile runTask arasında eklenti kapandı (IllegalPluginAccessException)
                plugin.getLogger().log(Level.WARNING,
                        "[ReportResolution] Could not schedule claim callback for report #" + reportId, t);
                if (finalResult == ClaimResult.CLAIMED) {
                    revertNow(plugin, reportId, target);
                }
            }
        });
    }

    /**
     * Ceza uygulanamadı: fromStatus -> PENDING (koşullu). Ateşle-unut, sonuç loglanır.
     * Eklenti kapanıyorsa (onDisable sırasında) async görev atılamayacağı için senkron çalışır.
     */
    public static void revert(ReportSystemSpigot plugin, int reportId, Report.Status fromStatus) {
        final String from = fromStatus.getValue();
        runDb(plugin, () -> revertNow(plugin, reportId, from));
    }

    /**
     * Kabul/ret sonrası DB işleri (async): ceza türü yazımı ve Overwatch oylamasının sonlandırılması.
     * @param punishmentTypeOrNull null ise ceza türü yazılmaz (cezasız kabul / ret)
     * @param guilty yetkili kararı (kabul = true, ret = false)
     * @param resolvedBy raporu çözen yetkilinin adı (H-14a; DB'ye kalıcı yazılır)
     */
    public static void finishAsync(ReportSystemSpigot plugin, int reportId, String punishmentTypeOrNull,
                                   boolean guilty, String resolvedBy) {
        final long resolvedAt = System.currentTimeMillis();
        runDb(plugin, () -> {
            try {
                if (!plugin.getReportService().setResolution(reportId, resolvedBy, resolvedAt)) {
                    plugin.getLogger().warning("[ReportResolution] Resolver could not be written for report #"
                            + reportId);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE,
                        "[ReportResolution] Resolver write error for report #" + reportId, t);
            }
            if (punishmentTypeOrNull != null) {
                try {
                    if (!plugin.getReportService().updateReportPunishment(reportId, punishmentTypeOrNull)) {
                        plugin.getLogger().warning("[ReportResolution] Punishment type could not be written for report #"
                                + reportId);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE,
                            "[ReportResolution] Punishment type write error for report #" + reportId, t);
                }
            }
            try {
                if (plugin.getOverwatchManager() != null) {
                    plugin.getOverwatchManager().resolveReportVoting(reportId, guilty);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE,
                        "[ReportResolution] Overwatch resolve error for report #" + reportId, t);
            }
        });
    }

    // --- iç yardımcılar ---

    /** Bloklayan geri alma; yalnız async görevden (veya kapanışta) çağrılır. */
    private static void revertNow(ReportSystemSpigot plugin, int reportId, String from) {
        try {
            int rows = plugin.getReportService().transitionStatus(reportId, from, PENDING);
            if (rows > 0) {
                plugin.getLogger().info("[ReportResolution] Report #" + reportId + " reverted " + from
                        + " -> " + PENDING + " (punishment could not be applied)");
            } else if (rows == 0) {
                plugin.getLogger().warning("[ReportResolution] Report #" + reportId + " was not in " + from
                        + " state; revert skipped");
            } else {
                plugin.getLogger().severe("[ReportResolution] Report #" + reportId + " revert failed (DB error)");
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[ReportResolution] Revert error for report #" + reportId, t);
        }
    }

    /**
     * DB işini async çalıştırır. YALNIZ kapanışta (eklenti devre dışı; onDisable içinde
     * AnimatedBanManager bekleyen banları uygularken) async görev atılamadığı için senkron
     * çalıştırılır — DB bu noktada henüz kapanmamıştır, sunucu zaten duruyor.
     */
    private static void runDb(ReportSystemSpigot plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[ReportResolution] Could not schedule DB task", t);
        }
    }
}
