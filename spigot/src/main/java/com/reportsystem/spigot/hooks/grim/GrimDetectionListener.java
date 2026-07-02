package com.reportsystem.spigot.hooks.grim;

import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.event.events.CommandExecuteEvent;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grim FlagEvent ve CommandExecuteEvent dinler.
 * Polar/Vulcan ile ayni multi-sinyal puanlama sistemi.
 *
 * Grim eventleri ASYNC (netty thread) — Bukkit API icin scheduler zorunlu.
 * Check isimleri String bazli: getCheckName() = "Reach", "Simulation", "TimerA"
 */
public class GrimDetectionListener {

    private final ReportSystemSpigot plugin;
    private final GrimAbstractAPI grimApi;

    private final Map<UUID, SuspicionData> suspicionMap = new ConcurrentHashMap<>();
    private int activeAutoRecordings = 0;

    // ─── Check Agirliklari ───

    private static final Map<String, Double> CHECK_WEIGHTS;
    static {
        Map<String, Double> w = new HashMap<>();
        // Combat
        w.put("Reach", 0.30);
        w.put("Hitboxes", 0.30);
        w.put("MultiInteractA", 0.20);
        w.put("MultiInteractB", 0.20);

        // Movement / Prediction
        w.put("Simulation", 0.10);    // Cok sik flag atar, dusuk puan
        w.put("Phase", 0.20);
        w.put("NoSlow", 0.15);
        w.put("FlightA", 0.15);
        w.put("GroundSpoof", 0.10);

        // Velocity
        w.put("Knockback", 0.25);
        w.put("Explosion", 0.20);

        // Timer
        w.put("TimerA", 0.25);
        w.put("NegativeTimer", 0.15);
        w.put("VehicleTimer", 0.15);

        // Scaffold / Block Placement
        w.put("AirLiquidPlace", 0.30);
        w.put("FabricatedPlace", 0.35);
        w.put("FarPlace", 0.20);
        w.put("MultiPlace", 0.25);
        w.put("InvalidPlaceA", 0.20);
        w.put("InvalidPlaceB", 0.20);

        // Breaking
        w.put("FastBreak", 0.25);
        w.put("MultiBreak", 0.25);
        w.put("FarBreak", 0.15);

        // Aim
        w.put("AimDuplicateLook", 0.20);
        w.put("AimModulo360", 0.30);

        // Baritone
        w.put("Baritone", 0.35);

        // Sprint
        w.put("Sprint", 0.05);

        CHECK_WEIGHTS = Collections.unmodifiableMap(w);
    }

    public GrimDetectionListener(ReportSystemSpigot plugin, GrimAbstractAPI grimApi) {
        this.plugin = plugin;
        this.grimApi = grimApi;
    }

    public void register() {
        // FlagEvent — ana tespit eventi (async, netty thread)
        grimApi.getEventBus().subscribe(plugin, FlagEvent.class, this::onFlag);

        // CommandExecuteEvent — Grim ceza komutu calistirinca (PunishEvent yok)
        grimApi.getEventBus().subscribe(plugin, CommandExecuteEvent.class, this::onCommand);

        // Puan decay — her 60 saniyede %50 azalt
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            suspicionMap.entrySet().removeIf(e -> e.getValue().score < 0.05);
            suspicionMap.values().forEach(data -> data.score *= 0.5);
        }, 1200L, 1200L);

        plugin.debug("[GRIM] FlagEvent + CommandExecuteEvent listener kaydedildi.");
    }

    public void unregister() {
        grimApi.getEventBus().unregisterAllListeners(plugin);
        suspicionMap.clear();
        plugin.debug("[GRIM] Listener'lar kaldirildi.");
    }

    // ─── Event Handlers (ASYNC — netty thread) ───

    private void onFlag(FlagEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("grim.enabled", false)) return;

        // POJO'ya cevir (async thread, GC'ye gidebilir)
        UUID uuid = event.getUser().getUniqueId();
        String playerName = event.getUser().getName();

        // Replay izleyenleri yok say (Timer/Knockback/Flight false-positive'leri)
        if (plugin.getAntiCheatBypassManager() != null
                && plugin.getAntiCheatBypassManager().isExempt(uuid)) {
            return;
        }
        String checkName = event.getCheck().getCheckName();
        double vl = event.getViolations();
        boolean isSetback = event.isSetback();
        int ping = event.getUser().getTransactionPing();
        String clientVersion = event.getUser().getVersionName();
        String clientBrand = event.getUser().getBrand();

        double weight = CHECK_WEIGHTS.getOrDefault(checkName, 0.10);

        // BadPackets kontrolleri (A-Z) — hepsi ayni agirlik
        if (checkName.startsWith("BadPackets")) weight = 0.15;
        // Crash kontrolleri — dusuk, genelde exploit
        if (checkName.startsWith("Crash")) weight = 0.10;

        // Setback olan flag'ler daha onemli
        if (isSetback) weight *= 1.3;

        double threshold = plugin.getConfig().getDouble("grim.suspicion-threshold", 1.0);

        SuspicionData data = suspicionMap.computeIfAbsent(uuid, k -> new SuspicionData());
        data.score += weight;
        data.lastCheckName = checkName;
        data.lastVl = vl;
        data.detectionCount++;
        data.lastDetectionTime = System.currentTimeMillis();

        plugin.debug("[GRIM] " + playerName + " +" + String.format("%.2f", weight) +
                " (" + checkName + ", VL:" + String.format("%.1f", vl) + ") = " +
                String.format("%.2f", data.score) + "/" + threshold);

        if (data.score < threshold) return;

        // Cooldown
        int cooldownSec = plugin.getConfig().getInt("grim.cooldown-seconds", 300);
        long now = System.currentTimeMillis();
        if (data.lastTriggerTime > 0 && (now - data.lastTriggerTime) < cooldownSec * 1000L) return;

        // Max esanli kayit
        int maxAutoRec = plugin.getConfig().getInt("grim.max-auto-recordings", 5);
        if (activeAutoRecordings >= maxAutoRec) return;
        if (plugin.getRecordingManager().isRecording(uuid)) return;

        // Tetikle
        data.lastTriggerTime = now;
        double finalScore = data.score;
        String triggerCheck = data.lastCheckName;
        data.score = 0;
        data.detectionCount = 0;

        int recDuration = plugin.getConfig().getInt("grim.recording-duration", 30);
        String reason = "[Grim] " + checkName + " | VL: " + String.format("%.1f", vl) +
                " | Skor: " + String.format("%.1f", finalScore);

        plugin.getLogger().info("[GRIM] Otomatik kayit: " + playerName +
                " (" + triggerCheck + ", Skor: " + String.format("%.2f", finalScore) + ")");

        // Ana thread'e gec (Bukkit API netty'den cagrilamaz)
        Bukkit.getScheduler().runTask(plugin, () ->
                startRecordingAndReport(uuid, playerName, reason, recDuration));
    }

    private void onCommand(CommandExecuteEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("grim.enabled", false)) return;

        UUID uuid = event.getUser().getUniqueId();
        String playerName = event.getUser().getName();
        String command = event.getCommand();
        String checkName = event.getCheck().getCheckName();

        plugin.getLogger().info("[GRIM] Ceza komutu: " + playerName + " (" + checkName + ") -> " + command);

        String reason = "[Grim] Ceza: " + checkName + " | " + command;

        // Ana thread'e gec
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getReportService().createReport(
                    "GrimAC", "00000000-0000-0000-0000-000000000000",
                    playerName, null,
                    reason, plugin.getServerName(), 0
            ).thenAccept(reportId -> {
                if (reportId > 0) {
                    plugin.getLogger().info("[GRIM] Ceza raporu #" + reportId + " - " + playerName);
                    sendWebhook(reportId);
                }
            });
        });
    }

    // ─── Kayit + Rapor ───

    private void startRecordingAndReport(UUID uuid, String playerName, String reason, int recDuration) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        plugin.getReportService().createReport(
                "GrimAC", "00000000-0000-0000-0000-000000000000",
                playerName, null,
                reason, plugin.getServerName(), 0
        ).thenAccept(reportId -> {
            if (reportId <= 0) return;

            plugin.getLogger().info("[GRIM] Rapor #" + reportId + " - " + playerName);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player target = Bukkit.getPlayer(uuid);
                if (target == null || !target.isOnline()) return;

                activeAutoRecordings++;
                plugin.getRecordingManager().startRecording(
                        target, reportId, recDuration, "GrimAC", reason
                );

                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        activeAutoRecordings = Math.max(0, activeAutoRecordings - 1),
                        recDuration * 20L + 40L);

                if (plugin.getOverwatchManager() != null
                        && plugin.getConfig().getBoolean("grim.add-to-overwatch", true)) {
                    try {
                        plugin.getOverwatchManager().addReportToQueue(reportId, 2);
                    } catch (Exception ignored) {}
                }

                sendWebhook(reportId);
            });
        });
    }

    private void sendWebhook(int reportId) {
        if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
            com.reportsystem.common.models.Report report = plugin.getReportService().getReportById(reportId);
            if (report != null) {
                plugin.getWebhookManager().sendNewReportNotification(report);
            }
        }
    }

    // ─── Veri Sinifi ───

    private static class SuspicionData {
        double score = 0;
        long lastDetectionTime = 0;
        long lastTriggerTime = 0;
        String lastCheckName = "";
        double lastVl = 0;
        int detectionCount = 0;
    }
}
