package com.reportsystem.spigot.hooks.polar;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.polar.api.PolarApi;
import top.polar.api.event.listener.ListenerPriority;
import top.polar.api.event.listener.RegisteredListener;
import top.polar.api.user.event.DetectionAlertEvent;
import top.polar.api.user.event.CloudDetectionEvent;
import top.polar.api.user.event.MitigationEvent;
import top.polar.api.user.event.PunishmentEvent;
import top.polar.api.user.event.type.CheckType;
import top.polar.api.user.event.type.CloudCheckType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Polar detection eventlerini dinler.
 *
 * Multi-sinyal puanlama sistemi:
 * - DetectionAlertEvent (sunucu tarafi)  → check tipine gore puan
 * - CloudDetectionEvent (ML tabanlı)     → yuksek puan (cloud guvenilir)
 * - MitigationEvent (aktif engelleme)    → dusuk puan (kanit biriktirme)
 * - PunishmentEvent (Polar ceza veriyor) → aninda rapor (kayit gereksiz, oyuncu gidecek)
 *
 * Puan esigi asilinca: otomatik kayit + rapor + Overwatch kuyrugu.
 */
public class PolarDetectionListener {

    private final ReportSystemSpigot plugin;
    private final PolarApi polarApi;

    // Listener referanslari
    private RegisteredListener<DetectionAlertEvent> detectionReg;
    private RegisteredListener<CloudDetectionEvent> cloudReg;
    private RegisteredListener<MitigationEvent> mitigationReg;
    private RegisteredListener<PunishmentEvent> punishmentReg;

    // Oyuncu basi suphe verisi
    private final Map<UUID, SuspicionData> suspicionMap = new ConcurrentHashMap<>();

    // Aktif otomatik kayit sayaci
    private int activeAutoRecordings = 0;

    // ─── Sinyal Agirliklari ───

    // Sunucu tarafi detection (VL bazli checkler)
    private static final Map<CheckType, Double> DETECTION_WEIGHTS;
    static {
        Map<CheckType, Double> w = new HashMap<>();
        w.put(CheckType.KILL_AURA, 0.35);
        w.put(CheckType.TICK_SPEED, 0.30);
        w.put(CheckType.CLOUD, 0.25);
        w.put(CheckType.BLOCK_INTERACT, 0.20);
        w.put(CheckType.OCCLUDED_INTERACT, 0.20);
        w.put(CheckType.PHASE, 0.20);
        w.put(CheckType.LATENCY_ABUSE, 0.15);
        w.put(CheckType.GROUND_SPOOF, 0.10);
        w.put(CheckType.REACH, 0.10);       // Mitigated, dusuk puan
        w.put(CheckType.MOVEMENT, 0.05);     // Mitigated, cok dusuk
        w.put(CheckType.VELOCITY, 0.05);     // Mitigated
        DETECTION_WEIGHTS = Collections.unmodifiableMap(w);
    }

    // Cloud ML detection (yuksek guvenilirlik)
    private static final Map<CloudCheckType, Double> CLOUD_WEIGHTS;
    static {
        Map<CloudCheckType, Double> c = new HashMap<>();
        c.put(CloudCheckType.COMBAT_BEHAVIOR, 0.50);  // En onemli — killaura/aimbot
        c.put(CloudCheckType.AUTO_CLICKER, 0.45);
        c.put(CloudCheckType.SCAFFOLD, 0.40);
        c.put(CloudCheckType.INVALID_PROTOCOL, 0.35);
        c.put(CloudCheckType.CHEST_STEALER, 0.30);
        c.put(CloudCheckType.LATENCY_ABUSE, 0.25);
        c.put(CloudCheckType.CPS_LIMIT, 0.20);
        c.put(CloudCheckType.RIGHT_CPS_LIMIT, 0.15);
        CLOUD_WEIGHTS = Collections.unmodifiableMap(c);
    }

    private static final double MITIGATION_WEIGHT = 0.08;

    public PolarDetectionListener(ReportSystemSpigot plugin, PolarApi polarApi) {
        this.plugin = plugin;
        this.polarApi = polarApi;
    }

    public void register() {
        var repository = polarApi.events().repository();

        // 1. Sunucu tarafi tespitler
        Consumer<DetectionAlertEvent> detectionHandler = event -> {
            if (event.cancelled()) return;

            // POJO'ya cevir (API objeleri accessor, GC'ye gidebilir)
            UUID uuid = event.user().uuid();
            String name = event.user().username();

            // Replay izleyenleri yok say
            if (plugin.getAntiCheatBypassManager() != null
                    && plugin.getAntiCheatBypassManager().isExempt(uuid)) {
                return;
            }
            CheckType type = event.check().type();
            String checkName = event.check().name();
            double vl = event.check().violationLevel();
            double punishVl = event.check().punishVl();
            long latency = event.user().connection().latency();
            String clientVersion = event.user().clientVersion().name();
            String clientBrand = event.user().clientVersion().brand();

            double weight = DETECTION_WEIGHTS.getOrDefault(type, 0.10);

            // VL oranini agirliga dahil et (punishVl'ye yaklastikca daha onemli)
            double vlMultiplier = punishVl > 0 ? Math.min(vl / punishVl, 1.5) : 1.0;
            double score = weight * (0.5 + vlMultiplier * 0.5);

            addSuspicion(uuid, name, score, type.name(), checkName, vl, punishVl,
                    latency, clientVersion, clientBrand, "DETECTION");
        };

        // 2. Cloud ML tespitler (yuksek guvenilirlik)
        Consumer<CloudDetectionEvent> cloudHandler = event -> {
            if (event.cancelled()) return;

            UUID uuid = event.user().uuid();
            String name = event.user().username();

            if (plugin.getAntiCheatBypassManager() != null
                    && plugin.getAntiCheatBypassManager().isExempt(uuid)) {
                return;
            }

            CloudCheckType cloudType = event.check().type();
            Set<String> tags = event.check().tags();
            long latency = event.user().connection().latency();
            String clientVersion = event.user().clientVersion().name();
            String clientBrand = event.user().clientVersion().brand();

            double weight = CLOUD_WEIGHTS.getOrDefault(cloudType, 0.20);

            addSuspicion(uuid, name, weight, "CLOUD_" + cloudType.name(),
                    cloudType.name() + (tags.isEmpty() ? "" : " " + tags),
                    0, 0, latency, clientVersion, clientBrand, "CLOUD");
        };

        // 3. Mitigation (aktif engelleme — kanit biriktirme)
        Consumer<MitigationEvent> mitigationHandler = event -> {
            if (event.cancelled() || event.verbose()) return; // verbose = bilgi amacli, onemli degil

            UUID uuid = event.user().uuid();
            String name = event.user().username();

            if (plugin.getAntiCheatBypassManager() != null
                    && plugin.getAntiCheatBypassManager().isExempt(uuid)) {
                return;
            }

            CheckType type = event.check().type();
            String checkName = event.check().name();

            addSuspicion(uuid, name, MITIGATION_WEIGHT, type.name(), checkName,
                    event.check().violationLevel(), event.check().punishVl(),
                    event.user().connection().latency(),
                    event.user().clientVersion().name(),
                    event.user().clientVersion().brand(),
                    "MITIGATION");
        };

        // 4. Punishment (Polar ceza veriyor — oyuncu gidecek, sadece rapor olustur)
        Consumer<PunishmentEvent> punishmentHandler = event -> {
            if (event.cancelled()) return;

            UUID uuid = event.user().uuid();
            String name = event.user().username();

            if (plugin.getAntiCheatBypassManager() != null
                    && plugin.getAntiCheatBypassManager().isExempt(uuid)) {
                return;
            }

            String reason = event.reason();
            String pType = event.type().name();
            Set<CloudCheckType> cloudChecks = event.cloudCheckTypes();
            long latency = event.user().connection().latency();
            String clientVersion = event.user().clientVersion().name();
            String clientBrand = event.user().clientVersion().brand();

            plugin.debug("[POLAR] Punishment tetiklendi: " + name + " (" + pType + ") - " + reason);

            // Oyuncu ban/kick yiyecek — kayit baslatmanin anlami yok
            // Sadece rapor olustur
            createPunishmentReport(uuid, name, pType, reason, cloudChecks,
                    latency, clientVersion, clientBrand);
        };

        detectionReg = repository.registerListener(DetectionAlertEvent.class, detectionHandler, ListenerPriority.RUN_LAST);
        cloudReg = repository.registerListener(CloudDetectionEvent.class, cloudHandler, ListenerPriority.RUN_LAST);
        mitigationReg = repository.registerListener(MitigationEvent.class, mitigationHandler, ListenerPriority.RUN_LAST);
        punishmentReg = repository.registerListener(PunishmentEvent.class, punishmentHandler, ListenerPriority.RUN_LAST);

        // Puan decay task — her 60 saniyede %50 azalt
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            suspicionMap.entrySet().removeIf(e -> e.getValue().score < 0.05);
            suspicionMap.values().forEach(data -> data.score *= 0.5);
        }, 1200L, 1200L);

        plugin.debug("[POLAR] 4 event listener kaydedildi (Detection, Cloud, Mitigation, Punishment)");
    }

    public void unregister() {
        var repository = polarApi.events().repository();
        if (detectionReg != null) repository.unregisterListener(detectionReg);
        if (cloudReg != null) repository.unregisterListener(cloudReg);
        if (mitigationReg != null) repository.unregisterListener(mitigationReg);
        if (punishmentReg != null) repository.unregisterListener(punishmentReg);
        detectionReg = null;
        cloudReg = null;
        mitigationReg = null;
        punishmentReg = null;
        suspicionMap.clear();
        plugin.debug("[POLAR] Listener'lar kaldirildi.");
    }

    // ─── Suphe Biriktirme ───

    private void addSuspicion(UUID uuid, String playerName, double score,
                               String checkType, String checkName, double vl, double punishVl,
                               long latency, String clientVersion, String clientBrand,
                               String source) {

        if (!plugin.getConfig().getBoolean("polar.enabled", false)) return;

        double threshold = plugin.getConfig().getDouble("polar.suspicion-threshold", 1.0);
        int cooldownSec = plugin.getConfig().getInt("polar.cooldown-seconds", 300);
        int maxAutoRec = plugin.getConfig().getInt("polar.max-auto-recordings", 5);
        int recDuration = plugin.getConfig().getInt("polar.recording-duration", 30);

        SuspicionData data = suspicionMap.computeIfAbsent(uuid, k -> new SuspicionData());
        data.score += score;
        data.lastCheckType = checkType;
        data.lastCheckName = checkName;
        data.lastVl = vl;
        data.lastPunishVl = punishVl;
        data.latency = latency;
        data.clientVersion = clientVersion;
        data.clientBrand = clientBrand;
        data.lastSource = source;
        data.detectionCount++;
        data.lastDetectionTime = System.currentTimeMillis();

        plugin.debug("[POLAR] " + playerName + " +" + String.format("%.2f", score) +
                " (" + source + "/" + checkType + ") = " + String.format("%.2f", data.score) +
                "/" + threshold);

        // Esik kontrolu
        if (data.score < threshold) return;

        // Cooldown kontrolu
        long now = System.currentTimeMillis();
        if (data.lastTriggerTime > 0 && (now - data.lastTriggerTime) < cooldownSec * 1000L) return;

        // Max esanli kayit
        if (activeAutoRecordings >= maxAutoRec) return;

        // Zaten kaydi var mi?
        if (plugin.getRecordingManager().isRecording(uuid)) return;

        // Tetikle!
        data.lastTriggerTime = now;
        double finalScore = data.score;
        int totalDetections = data.detectionCount;
        data.score = 0;
        data.detectionCount = 0;

        String reason = buildReason(checkType, checkName, source, vl, punishVl);
        String details = buildDetails(playerName, finalScore, totalDetections, latency, clientVersion, clientBrand);

        plugin.getLogger().info("[POLAR] Otomatik kayit: " + playerName +
                " (Skor: " + String.format("%.2f", finalScore) + ", " + source + "/" + checkType + ")");

        // Ana thread'de kayit + rapor (Bukkit API netty thread'den cagrilamaz)
        Bukkit.getScheduler().runTask(plugin, () ->
                startRecordingAndReport(uuid, playerName, reason, details, recDuration));
    }

    // ─── Punishment Raporu (kayit yok, oyuncu gidecek) ───

    private void createPunishmentReport(UUID uuid, String playerName, String punishmentType,
                                         String reason, Set<CloudCheckType> cloudChecks,
                                         long latency, String clientVersion, String clientBrand) {

        StringBuilder reportReason = new StringBuilder("[Polar] " + punishmentType + " - " + reason);
        if (!cloudChecks.isEmpty()) {
            reportReason.append(" | Cloud: ");
            cloudChecks.forEach(c -> reportReason.append(c.name()).append(" "));
        }

        String finalReason = reportReason.toString().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getReportService().createReport(
                    "PolarAC", "00000000-0000-0000-0000-000000000000",
                    playerName, null,
                    finalReason, plugin.getServerName(),
                    0
            ).thenAccept(reportId -> {
                if (reportId > 0) {
                    plugin.getLogger().info("[POLAR] Ceza raporu #" + reportId + " - " + playerName +
                            " (" + finalReason + ")");

                    // Webhook bildirim
                    sendWebhook(reportId);
                }
            });
        });
    }

    // ─── Kayit + Rapor Olusturma ───

    private void startRecordingAndReport(UUID uuid, String playerName, String reason,
                                          String details, int recordingDuration) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        // Rapor olustur
        plugin.getReportService().createReport(
                "PolarAC", "00000000-0000-0000-0000-000000000000",
                playerName, null,
                reason, plugin.getServerName(),
                0
        ).thenAccept(reportId -> {
            if (reportId <= 0) {
                plugin.debug("[POLAR] Rapor olusturulamadi: " + playerName);
                return;
            }

            plugin.getLogger().info("[POLAR] Rapor #" + reportId + " - " + playerName + " | " + details);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player target = Bukkit.getPlayer(uuid);
                if (target == null || !target.isOnline()) return;

                // Kayit baslat
                activeAutoRecordings++;
                plugin.getRecordingManager().startRecording(
                        target, reportId, recordingDuration, "PolarAC", reason
                );

                // Kayit bitince counter azalt
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        activeAutoRecordings = Math.max(0, activeAutoRecordings - 1),
                        recordingDuration * 20L + 40L);

                // Overwatch kuyruğuna ekle
                if (plugin.getOverwatchManager() != null
                        && plugin.getConfig().getBoolean("polar.add-to-overwatch", true)) {
                    try {
                        plugin.getOverwatchManager().addReportToQueue(reportId, 2); // Oncelik 2 (yuksek)
                        plugin.debug("[POLAR] Rapor #" + reportId + " Overwatch kuyruğuna eklendi.");
                    } catch (Exception e) {
                        plugin.debug("[POLAR] Overwatch kuyruk hatasi: " + e.getMessage());
                    }
                }

                // Webhook bildirim
                sendWebhook(reportId);
            });
        });
    }

    // ─── Yardimci Metodlar ───

    private void sendWebhook(int reportId) {
        if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
            com.reportsystem.common.models.Report report = plugin.getReportService().getReportById(reportId);
                if (report != null) {
                    plugin.getWebhookManager().sendNewReportNotification(report);
                }
        }
    }

    private String buildReason(String checkType, String checkName, String source, double vl, double punishVl) {
        String cleanType = checkType.replace("CLOUD_", "");
        String formatted = formatCheckName(cleanType);

        StringBuilder sb = new StringBuilder("[Polar] ");
        sb.append(formatted);
        if (!checkName.equals(cleanType)) {
            sb.append(" (").append(checkName).append(")");
        }
        if ("CLOUD".equals(source)) {
            sb.append(" [Cloud ML]");
        }
        if (vl > 0 && punishVl > 0) {
            sb.append(" | VL: ").append(String.format("%.1f/%.1f", vl, punishVl));
        }
        return sb.toString();
    }

    private String buildDetails(String playerName, double score, int detections,
                                 long latency, String clientVersion, String clientBrand) {
        return playerName + " | Skor: " + String.format("%.2f", score) +
                " | " + detections + " tespit" +
                " | " + clientVersion + " " + clientBrand +
                " | " + latency + "ms";
    }

    private String formatCheckName(String raw) {
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    // ─── Veri Siniflari ───

    private static class SuspicionData {
        double score = 0;
        long lastDetectionTime = 0;
        long lastTriggerTime = 0;
        String lastCheckType = "";
        String lastCheckName = "";
        String lastSource = "";
        double lastVl = 0;
        double lastPunishVl = 0;
        long latency = 0;
        String clientVersion = "";
        String clientBrand = "";
        int detectionCount = 0;
    }
}
