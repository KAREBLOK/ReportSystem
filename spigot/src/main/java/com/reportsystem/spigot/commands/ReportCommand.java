package com.reportsystem.spigot.commands;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.gui.ReportCreateGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportSystemSpigot plugin;
    private static final Map<UUID, Integer> pendingReportIds = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> reportTimestamps = new HashMap<>();

    public ReportCommand(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "general.only-players");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("reportsystem.report")) {
            plugin.getMessageManager().sendNoPermission(player);
            return true;
        }

        if (args.length == 0) {
            String usage = plugin.getMessageManager().getMessage("commands.report.usage");
            plugin.getMessageManager().sendMessage(player, "general.invalid-syntax", "%usage%", usage);
            return true;
        }

        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);

        // Kendini rapor edemez
        if (player.getName().equalsIgnoreCase(targetName)) {
            plugin.getMessageManager().sendMessage(player, "reports.cannot-report-self");
            return true;
        }

        // Çevrimdışı oyuncu kontrolü
        boolean requireOnline = plugin.getConfig().getBoolean("reports.require-online", true);
        if (requireOnline && targetPlayer == null) {
            plugin.getMessageManager().sendMessage(player, "reports.cannot-report-offline", "%player%", targetName);
            return true;
        }

        // Immune kontrolü - Hedef oyuncu report edilemez izni var mı?
        if (targetPlayer != null && targetPlayer.hasPermission("reportsystem.exempt")) {
            plugin.getMessageManager().sendCannotReportImmune(player);
            return true;
        }

        // Aktif kayıt kontrolü - Oyuncu zaten kaydediliyorsa tekrar report edilemesin
        if (targetPlayer != null && plugin.getRecordingManager().isRecording(targetPlayer.getUniqueId())) {
            plugin.getMessageManager().sendMessage(player, "reports.already-recording", "%player%", targetName);
            return true;
        }

        // Cooldown kontrolü
        // Note: checkCooldown() both checks AND sets the cooldown. This means the
        // cooldown is
        // consumed when the GUI opens, even if the player cancels without submitting a
        // report.
        // This is intentional behavior to prevent spam-opening the report GUI.
        String cooldownKey = "report:" + player.getUniqueId();
        int cooldownSeconds = plugin.getConfigManager().getReportCooldown();

        if (cooldownSeconds > 0 && !player.hasPermission("reportsystem.cooldown.bypass")) {
            if (!plugin.checkCooldown(cooldownKey, cooldownSeconds)) {
                long remaining = plugin.getRemainingCooldown(cooldownKey, cooldownSeconds);
                plugin.getMessageManager().sendMessage(player, "general.cooldown", "%time%", remaining + " seconds");
                return true;
            }
        }

        // Global rate limit kontrolü (HERKES için geçerli, bypass edilemez)
        int globalRateLimit = plugin.getConfigManager().getGlobalRateLimit();
        if (globalRateLimit > 0) {
            UUID playerUUID = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long oneMinuteAgo = currentTime - 60000; // 1 dakika önce

            // Oyuncunun son report zamanlarını al
            List<Long> timestamps = reportTimestamps.getOrDefault(playerUUID, new ArrayList<>());

            // 1 dakikadan eski kayıtları temizle
            timestamps.removeIf(time -> time < oneMinuteAgo);

            // Limit kontrolü
            if (timestamps.size() >= globalRateLimit) {
                plugin.getMessageManager().sendRateLimitError(player, globalRateLimit);
                return true;
            }

            // Yeni zamanı ekle
            timestamps.add(currentTime);
            reportTimestamps.put(playerUUID, timestamps);
        }

        // GUI aç (kayıt GUI'den sebep seçildikten sonra başlayacak)
        new ReportCreateGUI(plugin, player, targetName).open();

        return true;
    }

    private void startRecording(Player reporter, Player target) {
        // Zaten kayıt ediliyor mu kontrol et
        if (plugin.getRecordingManager().isRecording(target.getUniqueId())) {
            return;
        }

        // Geçici ID ile kayıt başlat
        int tempReportId = -999;
        int duration = plugin.getConfigManager().getRecordingDuration();

        // Reason henüz bilinmiyor, GUI'den seçilecek
        plugin.getRecordingManager().startRecording(target, tempReportId, duration, reporter.getName(), "Bekleniyor");
        plugin.getMessageManager().sendRecordingStarted(reporter);

        plugin.getLogger().info("[RECORDING] Started recording for " + target.getName() +
                " (requested by " + reporter.getName() + ") with temp ID: " + tempReportId);
    }

    public static void updateRecordingWithReportId(UUID targetUUID, int reportId) {
        ReportSystemSpigot plugin = ReportSystemSpigot.getInstance();

        // Pending ID olarak sakla
        pendingReportIds.put(targetUUID, reportId);

        // Recording manager'a bildir
        if (plugin.getRecordingManager().isRecording(targetUUID)) {
            plugin.getRecordingManager().updateReportId(targetUUID, reportId);
            plugin.getLogger().info("[RECORDING] Updated recording for " + targetUUID +
                    " with actual report ID: " + reportId);
        } else {
            plugin.getLogger().warning("[RECORDING] No active recording found for " + targetUUID +
                    " when trying to update report ID: " + reportId);
        }
    }

    public static Integer getPendingReportId(UUID playerUUID) {
        return pendingReportIds.remove(playerUUID);
    }

    public void notifyStaff(String reporterName, String targetName, String reason, int reportId) {
        if (plugin.getConfigManager().isStaffNotificationEnabled()) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("reportsystem.notify")) {
                    plugin.getMessageManager().sendReportNotification(
                            staff, reporterName, targetName, reason, reportId);
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}