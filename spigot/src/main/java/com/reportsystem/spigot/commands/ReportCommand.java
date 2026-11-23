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
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportSystemSpigot plugin;
    // Yalnız ana thread'den (komut) erişilir
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

            // 1 dakikadan eski kayıtları temizle; listesi boşalan oyuncuyu haritadan at
            // (D5: aksi halde bir kez rapor eden her oyuncu sonsuza dek haritada kalıyordu)
            reportTimestamps.entrySet().removeIf(entry -> {
                entry.getValue().removeIf(time -> time < oneMinuteAgo);
                return entry.getValue().isEmpty();
            });

            // Oyuncunun son report zamanlarını al
            List<Long> timestamps = reportTimestamps.computeIfAbsent(playerUUID, k -> new ArrayList<>());

            // Limit kontrolü
            if (timestamps.size() >= globalRateLimit) {
                plugin.getMessageManager().sendRateLimitError(player, globalRateLimit);
                return true;
            }

            // Yeni zamanı ekle
            timestamps.add(currentTime);
        }

        // GUI aç (kayıt GUI'den sebep seçildikten sonra başlayacak)
        new ReportCreateGUI(plugin, player, targetName).open();

        return true;
    }

    /**
     * Açık kayda yeni raporu bağlar (H-40: birincil rapor EZİLMEZ, ek rapor olarak eklenir).
     * Bu yolu kullananlar (ChatListener) bildirimi zaten anında gönderdiği için
     * kayıt sonu bildirimi istenmez.
     */
    public static void updateRecordingWithReportId(UUID targetUUID, int reportId) {
        ReportSystemSpigot plugin = ReportSystemSpigot.getInstance();

        if (plugin.getRecordingManager().isRecording(targetUUID)) {
            plugin.getRecordingManager().attachReport(targetUUID, reportId, false);
        } else {
            plugin.getLogger().warning("[RECORDING] No active recording found for " + targetUUID +
                    " when trying to attach report ID: " + reportId);
        }
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
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}