package com.reportsystem.spigot.hooks.vulcan;

import com.reportsystem.spigot.ReportSystemSpigot;
import me.frep.vulcan.api.event.VulcanFlagEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vulcan flag/punish eventlerini dinler.
 * Polar ile ayni multi-sinyal puanlama sistemi.
 */
public class VulcanDetectionListener implements Listener {

    private final ReportSystemSpigot plugin;
    private final Map<UUID, SuspicionData> suspicionMap = new ConcurrentHashMap<>();
    private int activeAutoRecordings = 0;

    private static final Map<String, Double> CHECK_WEIGHTS;
    static {
        Map<String, Double> w = new HashMap<>();
        // Combat (yuksek)
        w.put("KillAura", 0.40);
        w.put("Aim", 0.25);
        w.put("AutoClicker", 0.35);
        w.put("Reach", 0.30);
        w.put("Hitbox", 0.30);
        w.put("Velocity", 0.20);
        w.put("AutoBlock", 0.20);
        w.put("Criticals", 0.25);
        // Movement (orta — setback ile engellenir)
        w.put("Flight", 0.15);
        w.put("Speed", 0.10);
        w.put("Jesus", 0.10);
        w.put("Step", 0.10);
        w.put("NoSlow", 0.10);
        w.put("Strafe", 0.08);
        w.put("Sprint", 0.05);
        w.put("Motion", 0.05);
        w.put("Jump", 0.05);
        w.put("FastClimb", 0.10);
        w.put("WallClimb", 0.10);
        w.put("BoatFly", 0.15);
        w.put("EntitySpeed", 0.10);
        w.put("Elytra", 0.10);
        // Player (degisken)
        w.put("Scaffold", 0.35);
        w.put("Tower", 0.30);
        w.put("BadPackets", 0.15);
        w.put("Timer", 0.25);
        w.put("FastBreak", 0.15);
        w.put("FastPlace", 0.15);
        w.put("FastUse", 0.10);
        w.put("Inventory", 0.20);
        w.put("GroundSpoof", 0.10);
        w.put("Improbable", 0.30);
        w.put("Invalid", 0.15);
        w.put("PingSpoof", 0.20);
        w.put("Baritone", 0.35);
        w.put("HackedClient", 0.50);
        w.put("Crash", 0.10);
        CHECK_WEIGHTS = Collections.unmodifiableMap(w);
    }

    public VulcanDetectionListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;

        // Puan decay — her 60 saniyede %50 azalt
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            suspicionMap.entrySet().removeIf(e -> e.getValue().score < 0.05);
            suspicionMap.values().forEach(data -> data.score *= 0.5);
        }, 1200L, 1200L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVulcanFlag(VulcanFlagEvent event) {
        if (!plugin.getConfig().getBoolean("vulcan.enabled", false))
            return;

        Player player = event.getPlayer();

        // Replay izleyenlerin flag'lerini yok say (Timer/Blink/Flight false-positive'leri)
        if (plugin.getAntiCheatBypassManager() != null
                && plugin.getAntiCheatBypassManager().isExempt(player)) {
            return;
        }

        String checkName = event.getCheck().getName();
        String checkType = String.valueOf(event.getCheck().getType());
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        double weight = CHECK_WEIGHTS.getOrDefault(checkName, 0.10);
        double threshold = plugin.getConfig().getDouble("vulcan.suspicion-threshold", 1.0);

        SuspicionData data = suspicionMap.computeIfAbsent(uuid, k -> new SuspicionData());
        data.score += weight;
        data.lastCheckName = checkName;
        data.lastCheckType = checkType;
        data.detectionCount++;
        data.lastDetectionTime = System.currentTimeMillis();

        plugin.debug("[VULCAN] " + playerName + " +" + String.format("%.2f", weight) +
                " (" + checkName + " " + checkType + ") = " +
                String.format("%.2f", data.score) + "/" + threshold);

        if (data.score < threshold)
            return;

        int cooldownSec = plugin.getConfig().getInt("vulcan.cooldown-seconds", 300);
        long now = System.currentTimeMillis();
        if (data.lastTriggerTime > 0 && (now - data.lastTriggerTime) < cooldownSec * 1000L)
            return;

        int maxAutoRec = plugin.getConfig().getInt("vulcan.max-auto-recordings", 5);
        if (activeAutoRecordings >= maxAutoRec)
            return;
        if (plugin.getRecordingManager().isRecording(uuid))
            return;

        // Tetikle
        data.lastTriggerTime = now;
        double finalScore = data.score;
        data.score = 0;
        data.detectionCount = 0;

        int recDuration = plugin.getConfig().getInt("vulcan.recording-duration", 30);
        String reason = "[Vulcan] " + checkName + " " + checkType +
                " | Skor: " + String.format("%.1f", finalScore);

        plugin.getLogger().info("[VULCAN] Otomatik kayit: " + playerName +
                " (" + checkName + " " + checkType + ", Skor: " + String.format("%.2f", finalScore) + ")");

        startRecordingAndReport(uuid, playerName, reason, recDuration);
    }

    private void startRecordingAndReport(UUID uuid, String playerName, String reason, int recDuration) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline())
            return;

        plugin.getReportService().createReport(
                "VulcanAC", "00000000-0000-0000-0000-000000000000",
                playerName, null,
                reason, plugin.getServerName(),
                0).thenAccept(reportId -> {
                    if (reportId <= 0)
                        return;

                    plugin.getLogger().info("[VULCAN] Rapor #" + reportId + " - " + playerName);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target == null || !target.isOnline())
                            return;

                        activeAutoRecordings++;
                        plugin.getRecordingManager().startRecording(
                                target, reportId, recDuration, "VulcanAC", reason);

                        Bukkit.getScheduler().runTaskLater(plugin,
                                () -> activeAutoRecordings = Math.max(0, activeAutoRecordings - 1),
                                recDuration * 20L + 40L);

                        if (plugin.getOverwatchManager() != null
                                && plugin.getConfig().getBoolean("vulcan.add-to-overwatch", true)) {
                            try {
                                plugin.getOverwatchManager().addReportToQueue(reportId, 2);
                            } catch (Exception ignored) {
                            }
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

    public void shutdown() {
        HandlerList.unregisterAll(this);
        suspicionMap.clear();
    }

    private static class SuspicionData {
        double score = 0;
        long lastDetectionTime = 0;
        long lastTriggerTime = 0;
        String lastCheckName = "";
        String lastCheckType = "";
        int detectionCount = 0;
    }
}
