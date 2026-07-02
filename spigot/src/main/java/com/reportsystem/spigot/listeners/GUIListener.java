package com.reportsystem.spigot.listeners;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.gui.*;
import com.reportsystem.spigot.punishment.PunishmentManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.UUID;

public class GUIListener implements Listener {

    private final ReportSystemSpigot plugin;

    public GUIListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        // DÜZELTİLDİ: Artık tüm GUI'lerimiz GUI sınıfını temel aldığı için bu basit kontrol yeterli.
        // ReportDetailGUI ve PunishmentSelectionGUI için özel kontrol eklendi (InventoryHolder implement eder)
        Object holder = event.getInventory().getHolder();
        if (!(holder instanceof GUI) && !(holder instanceof ReportDetailGUI) && !(holder instanceof PunishmentSelectionGUI)) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() == null || event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        ItemStack item = event.getCurrentItem();
        ClickType clickType = event.getClick();

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        // ReportCreateGUI kendi handleClick'ini kullanır
        if (holder instanceof ReportCreateGUI) {
            ((ReportCreateGUI) holder).handleClick(event);
        } else if (holder instanceof ReportListGUI) {
            handleReportListGUI(player, (ReportListGUI) holder, slot);
        } else if (holder instanceof ReportDetailGUI) {
            handleReportDetailGUI(player, (ReportDetailGUI) holder, slot);
        } else if (holder instanceof PunishmentSelectionGUI) {
            ((PunishmentSelectionGUI) holder).handleClick(event);
        } else if (holder instanceof PunishmentGUI) {
            handlePunishmentGUI(player, (PunishmentGUI) holder, item);
        } else if (holder instanceof com.reportsystem.spigot.gui.AnimatedBanGUI) {
            handleAnimatedBanGUI(player, (com.reportsystem.spigot.gui.AnimatedBanGUI) holder, item);
        } else if (holder instanceof ReplayControlGUI) {
            handleReplayControlGUI(player, (ReplayControlGUI) holder, slot, clickType.isLeftClick());
        } else if (holder instanceof ReplayInfoGUI) {
            ((ReplayInfoGUI) holder).handleClick(event);
        } else if (holder instanceof ReplayTeleportGUI) {
            ((ReplayTeleportGUI) holder).handleClick(event);
        }
    }

    // ... (Dosyanın geri kalanı önceki yanıttaki gibi doğru, değişiklik yok) ...

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GUI) {
            ((GUI) event.getInventory().getHolder()).handleClose(event);
        }

        // PunishmentSelectionGUI kapatıldığında rapor hala PENDING ise, hiçbir işlem yapma
        // Webhook sadece "Ceza Verme" butonuna basıldığında veya ceza uygulandığında gönderilir
    }

    private void handleReportListGUI(Player player, ReportListGUI gui, int slot) {
        if (slot == 48 && gui.getCurrentPage() > 1) {
            new ReportListGUI(plugin, player, gui.getCurrentPage() - 1).open();
        } else if (slot == 50 && gui.getCurrentPage() < gui.getMaxPage()) {
            new ReportListGUI(plugin, player, gui.getCurrentPage() + 1).open();
        } else if (slot == 45) {
            gui.refresh();
        } else {
            Report report = gui.getReportAtSlot(slot);
            if (report != null) {
                new ReportDetailGUI(player, report, plugin.getReplayDAO()).open();
            }
        }
    }

    private void handleReportDetailGUI(Player player, ReportDetailGUI gui, int slot) {
        Report report = gui.getReport();
        GUIConfig config = gui.getGuiConfig();

        if (slot == config.getItemSlot("replay")) {
            // Replay İzle
            if (gui.hasReplay()) {
                watchReplay(player, report.getId());
            } else {
                plugin.getMessageManager().sendMessage(player, "replay.not-found");
            }
        } else if (slot == config.getItemSlot("actions.accept")) {
            // Raporu Onayla - Ceza seçim GUI'sini aç
            acceptReportAndShowPunishmentSelection(player, report);
        } else if (slot == config.getItemSlot("actions.reject")) {
            // Raporu Reddet
            rejectReport(player, report);
        } else if (slot == config.getItemSlot("punishments.teleport")) {
            // Oyuncuya Işınlan
            player.closeInventory();
            teleportToPlayer(player, report.getReportedPlayerName());
        } else if (slot == config.getItemSlot("navigation.back")) {
            // Geri Dön
            player.closeInventory();
            new ReportListGUI(plugin, player, 1).open();
        }
    }

    /**
     * Animated Ban GUI handling - Süre seçimi
     */
    private void handleAnimatedBanGUI(Player staff, com.reportsystem.spigot.gui.AnimatedBanGUI gui, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // İptal veya geri butonu
        if (item.getType() == Material.RED_CONCRETE) {
            staff.closeInventory();
            return;
        }
        if (item.getType() == Material.ARROW) {
            new ReportDetailGUI(staff, gui.getReport(), plugin.getReplayDAO()).open();
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, "ban_duration");
            if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                String duration = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                Report report = gui.getReport();
                String targetName = gui.getTargetName();

                plugin.debug("[DEBUG] AnimatedBanGUI click - Duration: " + duration + ", Target: " + targetName);

                staff.closeInventory();

                // Özel süre mi?
                if (duration.equals("custom")) {
                    // Kullanıcıdan özel süre al
                    plugin.getChatInputManager().requestInput(staff,
                        plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("reports.chat.enter-duration")),
                        customDuration -> {
                            if (customDuration == null || customDuration.trim().isEmpty()) {
                                plugin.getMessageManager().sendMessage(staff, "reports.chat.invalid-duration");
                                return;
                            }
                            // Sebep girişi al
                            requestReasonAndExecuteBan(staff, report, targetName, customDuration.trim());
                        }
                    );
                } else {
                    // Direkt süre seçildi - sebep girişi al
                    requestReasonAndExecuteBan(staff, report, targetName, duration);
                }
            } else {
                plugin.debug("[DEBUG] AnimatedBanGUI click - No ban_duration data! Item: " + item.getType());
            }
        }
    }

    /**
     * Animasyonlu ban başlat (report sebabını kullan)
     */
    private void requestReasonAndExecuteBan(Player staff, Report report, String targetName, String duration) {
        // Report'un sebabini kullan
        String finalReason = report.getReason();

        // Parse süre
        long durationMillis;
        String durationText;

        if (duration.equals("permanent")) {
            durationMillis = -1; // Kalıcı
            durationText = plugin.getMessageManager().getMessage("misc.duration.permanent");
        } else {
            durationMillis = plugin.getPunishmentManager().parseDuration(duration);
            if (durationMillis <= 0) {
                plugin.getMessageManager().sendMessage(staff, "reports.chat.invalid-duration");
                return;
            }
            durationText = formatDuration(durationMillis);
        }

        // Hedef oyuncuyu bul
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            plugin.getMessageManager().sendMessage(staff, "punishments.ban.offline", "%player%", targetName);
            return;
        }

        // Animasyonlu ban başlat
        plugin.getAnimatedBanManager().executeAnimatedBan(target, finalReason, staff, durationMillis);

        // Başarı mesajı
        plugin.getMessageManager().sendMessage(staff, "punishments.ban.animated-started");
        plugin.getMessageManager().sendMessage(staff, "punishments.ban.animated-target", "%player%", targetName);
        plugin.getMessageManager().sendMessage(staff, "punishments.ban.animated-duration", "%duration%", durationText);
        plugin.getMessageManager().sendMessage(staff, "punishments.ban.animated-reason", "%reason%", finalReason);
        plugin.getMessageManager().sendMessage(staff, "punishments.ban.animated-message");

        // Raporu güncelle
        report.setStatus(Report.Status.ACCEPTED);
        report.setResolvedBy(staff.getName());
        report.setResolvedAt(System.currentTimeMillis());
        report.setPunished(true);
        report.setPunishmentType("BAN");

        try {
            plugin.getReportService().updateReport(report);

            // Overwatch kuyruğundan çıkar (artık PENDING değil)
            if (plugin.getOverwatchManager() != null) {
                plugin.getOverwatchManager().removeReportFromQueue(report.getId());
            }

            // Send single Discord webhook notification (report accepted + punishment applied)
            if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                plugin.getWebhookManager().sendReportClosedWithPunishmentNotification(
                        report,
                        staff.getName(),
                        "BAN",
                        finalReason,
                        durationText
                );
            }
            // Raporcu'ya bildirim
            notifyReporterIfOnline(report);
        } catch (SQLException e) {
            plugin.getLogger().warning("Error updating report after animated ban: " + e.getMessage());
        }
    }

    /**
     * Süreyi okunabilir formata çevir
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + " " + plugin.getMessageManager().getMessage("misc.time.days");
        if (hours > 0) return hours + " " + plugin.getMessageManager().getMessage("misc.time.hours");
        if (minutes > 0) return minutes + " " + plugin.getMessageManager().getMessage("misc.time.minutes");
        return seconds + " " + plugin.getMessageManager().getMessage("misc.time.seconds");
    }

    private void handlePunishmentGUI(Player player, PunishmentGUI gui, ItemStack item) {
        plugin.getLogger().info("[PunishmentGUI] Item clicked: " + item.getType());

        if (item.getType() == Material.RED_CONCRETE || item.getType() == Material.ARROW) {
            player.closeInventory();
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            plugin.getLogger().info("[PunishmentGUI] Meta is null!");
            return;
        }

        // Use PersistentDataContainer to get duration
        NamespacedKey durationKey = new NamespacedKey(plugin, "punishment_duration");
        String durationString = meta.getPersistentDataContainer().get(durationKey, PersistentDataType.STRING);

        plugin.getLogger().info("[PunishmentGUI] Duration from PDC: " + durationString);

        if (durationString != null && !durationString.isEmpty()) {
            player.closeInventory();
            String targetName = gui.getTargetName();
            String defaultReason = gui.getReport() != null ? gui.getReport().getReason() : plugin.getMessageManager().getMessage("punishments.default-reason");
            Report report = gui.getReport();

            plugin.getLogger().info("[PunishmentGUI] Duration: " + durationString + ", Target: " + targetName + ", Report: " + (report != null));

            // Eğer rapor varsa, raporun sebebini kullan ve direkt uygula
            if (report != null) {
                String finalReason = report.getReason();
                long durationMillis = plugin.getPunishmentManager().parseDuration(durationString);

                boolean success = false;
                String punishmentType = gui.getPunishmentType();

                if (punishmentType.equalsIgnoreCase("ban")) {
                    success = plugin.getPunishmentManager().getProvider().ban(targetName, finalReason, player.getName(), durationMillis);
                } else if (punishmentType.equalsIgnoreCase("mute")) {
                    success = plugin.getPunishmentManager().getProvider().mute(targetName, finalReason, player.getName(), durationMillis);
                } else if (punishmentType.equalsIgnoreCase("kick")) {
                    Player targetPlayer = Bukkit.getPlayer(targetName);
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        String kickMsg = plugin.getMessageManager().colorize(
                                plugin.getMessageManager().getMessage("punishments.kick.player-message")
                                        .replace("%reason%", finalReason)
                                        .replace("%staff%", player.getName()));
                        targetPlayer.kickPlayer(kickMsg);
                        success = true;
                    }
                }

                if (success) {
                    plugin.getMessageManager().sendMessage(player, "punishments.success-target", "%player%", targetName);

                    // Update report status
                    report.setStatus("ACCEPTED");
                    report.setPunished(true);
                    report.setPunishmentType(punishmentType.toUpperCase());

                    try {
                        plugin.getReportService().updateReport(report);
                    } catch (SQLException e) {
                        plugin.getLogger().severe("[PunishmentGUI] Failed to update report: " + e.getMessage());
                        e.printStackTrace();
                    }

                    // Send Discord webhook notification (report + punishment combined)
                    if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                        String durationText;
                        if (durationMillis == 0 || durationMillis == -1) {
                            durationText = "Permanent";
                        } else {
                            durationText = durationString; // Use the original duration string (e.g., "30d", "7d", "1h")
                        }
                        plugin.getWebhookManager().sendReportClosedWithPunishmentNotification(
                            report, player.getName(), punishmentType.toUpperCase(), finalReason, durationText
                        );
                    }
                } else {
                    plugin.getMessageManager().sendMessage(player, "punishments.failed");
                }
            } else {
                // Rapor yoksa chat input iste
                plugin.getChatInputManager().requestInput(player, plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("punishments.ban.enter-reason").replace("%default%", defaultReason)), reason -> {
                    String finalReason = (reason == null || reason.isEmpty()) ? defaultReason : reason;
                    long durationMillis = plugin.getPunishmentManager().parseDuration(durationString);

                    boolean success = false;
                    if (gui.getPunishmentType().equalsIgnoreCase("ban")) {
                        success = plugin.getPunishmentManager().getProvider().ban(targetName, finalReason, player.getName(), durationMillis);
                    } else if (gui.getPunishmentType().equalsIgnoreCase("mute")) {
                        success = plugin.getPunishmentManager().getProvider().mute(targetName, finalReason, player.getName(), durationMillis);
                    }

                    if (success) {
                        plugin.getMessageManager().sendMessage(player, "punishments.success-target", "%player%", targetName);
                    } else {
                        plugin.getMessageManager().sendMessage(player, "punishments.failed");
                    }
                });
            }
        }
    }

    // --- Yardımcı Metotlar ---

    private void createReport(Player reporter, String targetName, String reason) {
        reporter.closeInventory();
        if (reason.length() < plugin.getConfigManager().getMinReasonLength()) {
            plugin.getMessageManager().sendMessage(reporter, "reports.reason-too-short", "%min%", String.valueOf(plugin.getConfigManager().getMinReasonLength()));
            return;
        }

        plugin.getLogger().info("[ReportCreate] " + reporter.getName() + " -> " + targetName + " | Sebep: " + reason);

        plugin.getReportService().createReport(
                reporter.getName(),
                reporter.getUniqueId().toString(),
                targetName,
                "",
                reason,
                plugin.getServerName(),
                plugin.getConfigManager().getMaxReportsPerPlayer()
        ).thenAccept(reportId -> {
            if (reportId != null && reportId > 0) {
                plugin.getLogger().info("[ReportCreate] Rapor başarıyla oluşturuldu - ID: " + reportId);
                plugin.getMessageManager().sendReportSuccess(reporter, targetName, reportId);

                // Otomatik olarak Overwatch kuyruğuna ekle (sadece PENDING raporlar için)
                if (plugin.getOverwatchManager() != null) {
                    plugin.getOverwatchManager().addReportToQueue(reportId, 5); // Öncelik 5 (normal)
                }

                Player target = Bukkit.getPlayer(targetName);
                if (target != null && plugin.getRecordingManager().isRecording(target.getUniqueId())) {
                    plugin.getRecordingManager().updateReportIdAndReason(target.getUniqueId(), reportId, reason);
                }
                // Bildirim kayıt tamamlandığında gönderilecek
            } else if (reportId != null && reportId == -2) {
                // Limit exceeded
                plugin.getLogger().warning("[ReportCreate] Report limit exceeded: " + reporter.getName() + " -> " + targetName);
                plugin.getMessageManager().sendReportLimitReached(reporter, targetName, plugin.getConfigManager().getMaxReportsPerPlayer());
            } else {
                // General error (-1 or null)
                plugin.getLogger().severe("[ReportCreate] Report creation failed! ReportID: " + reportId);
                plugin.getMessageManager().sendMessage(reporter, "reports.create-failed");
                plugin.getMessageManager().sendMessage(reporter, "reports.contact-staff");
            }
        }).exceptionally(throwable -> {
            // Async error handling
            plugin.getLogger().severe("[ReportCreate] Async error: " + throwable.getMessage());
            throwable.printStackTrace();
            plugin.getMessageManager().sendMessage(reporter, "reports.unexpected-error");
            return null;
        });
    }

    /**
     * Ceza seçim GUI'sini göster (henüz rapor ACCEPTED olarak işaretlenmez)
     */
    private void acceptReportAndShowPunishmentSelection(Player staff, Report report) {
        // Overwatch kuyruğundan çıkar (artık PENDING değil)
        if (plugin.getOverwatchManager() != null) {
            plugin.getOverwatchManager().removeReportFromQueue(report.getId());
        }

        plugin.getMessageManager().sendMessage(staff, "reports.actions.select-punishment");

        // Ceza seçim GUI'sini aç (rapor henüz ACCEPTED değil, ceza seçildiğinde işaretlenecek)
        new PunishmentSelectionGUI(staff, report, plugin).open();
    }

    private void rejectReport(Player staff, Report report) {
        report.setStatus(Report.Status.REJECTED);
        report.setResolvedBy(staff.getName());
        report.setResolvedAt(System.currentTimeMillis());
        try {
            plugin.getReportService().updateReport(report);

            // Overwatch kuyruğundan çıkar (artık PENDING değil)
            if (plugin.getOverwatchManager() != null) {
                plugin.getOverwatchManager().removeReportFromQueue(report.getId());
            }

            // Send Discord webhook notification
            if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                plugin.getWebhookManager().sendReportClosedNotification(report, staff.getName(), report.getResponse());
            }

            staff.closeInventory();
            plugin.getMessageManager().sendMessage(staff, "reports.actions.rejected", "%id%", String.valueOf(report.getId()));
        } catch (SQLException e) {
            plugin.getMessageManager().sendMessage(staff, "reports.actions.database-error");
            e.printStackTrace();
        }
    }

    private void deleteReport(Player staff, Report report) {
        plugin.getReportService().deleteReport(report.getId());
        plugin.getReplayManager().deleteReplay(report.getId());
        staff.closeInventory();
        plugin.getMessageManager().sendMessage(staff, "reports.actions.deleted", "%id%", String.valueOf(report.getId()));
    }

    private void teleportToPlayer(Player staff, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.isOnline()) {
            staff.teleport(target);
            plugin.getMessageManager().sendMessage(staff, "general.teleported", "%player%", targetName);
        } else {
            plugin.getMessageManager().sendMessage(staff, "general.player-offline", "%player%", targetName);
        }
    }

    private void watchReplay(Player viewer, int reportId) {
        viewer.closeInventory();
        plugin.getReplayManager().startReplay(reportId, viewer);
    }


    /**
     * Kick handling - Oyuncuyu sunucudan at
     */
    private void handleKick(Player staff, Report report) {
        String targetName = report.getReportedPlayerName();
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            plugin.getMessageManager().sendMessage(staff, "punishments.kick.offline", "%player%", targetName);
            return;
        }

        staff.closeInventory();

        // Sebep girişi al
        String defaultReason = report.getReason();
        plugin.getChatInputManager().requestInput(staff,
            plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("punishments.kick.enter-reason").replace("%default%", defaultReason)),
            reason -> {
                String finalReason = (reason == null || reason.isEmpty()) ? defaultReason : reason;

                // Kick mesajı
                String kickMessage = plugin.getMessageManager().colorize(
                        plugin.getMessageManager().getMessage("punishments.kick.player-message")
                                .replace("%reason%", finalReason)
                                .replace("%staff%", staff.getName()));

                target.kickPlayer(kickMessage);

                // Başarı mesajı
                plugin.getMessageManager().sendMessage(staff, "punishments.kick.success", "%player%", targetName);

                // Raporu güncelle
                report.setStatus(Report.Status.ACCEPTED);
                report.setResolvedBy(staff.getName());
                report.setResolvedAt(System.currentTimeMillis());
                try {
                    plugin.getReportService().updateReport(report);

                    // Overwatch kuyruğundan çıkar (artık PENDING değil)
                    if (plugin.getOverwatchManager() != null) {
                        plugin.getOverwatchManager().removeReportFromQueue(report.getId());
                    }
                    // Raporcu'ya bildirim
                    notifyReporterIfOnline(report);
                } catch (SQLException e) {
                    plugin.getLogger().warning("Error updating report after kick: " + e.getMessage());
                }
            }
        );
    }

    /**
     * Warn handling - Oyuncuya uyarı gönder
     */
    private void handleWarn(Player staff, Report report) {
        String targetName = report.getReportedPlayerName();
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            plugin.getMessageManager().sendMessage(staff, "punishments.warn.offline", "%player%", targetName);
            return;
        }

        staff.closeInventory();

        // Sebep girişi al
        String defaultReason = report.getReason();
        plugin.getChatInputManager().requestInput(staff,
            plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("punishments.warn.enter-reason").replace("%default%", defaultReason)),
            reason -> {
                String finalReason = (reason == null || reason.isEmpty()) ? defaultReason : reason;

                // Uyarı mesajı gönder
                String warnMsg = plugin.getMessageManager().getMessage("punishments.warn.player-message")
                        .replace("%reason%", finalReason)
                        .replace("%staff%", staff.getName())
                        .replace("%count%", "");
                for (String line : warnMsg.split("\n")) {
                    target.sendMessage(plugin.getMessageManager().colorize(line));
                }

                // Ses efekti
                target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);

                // Başarı mesajı
                plugin.getMessageManager().sendMessage(staff, "punishments.warn.success", "%player%", targetName);

                // Raporu güncelle
                report.setStatus(Report.Status.ACCEPTED);
                report.setResolvedBy(staff.getName());
                report.setResolvedAt(System.currentTimeMillis());
                try {
                    plugin.getReportService().updateReport(report);

                    // Overwatch kuyruğundan çıkar (artık PENDING değil)
                    if (plugin.getOverwatchManager() != null) {
                        plugin.getOverwatchManager().removeReportFromQueue(report.getId());
                    }
                    // Raporcu'ya bildirim
                    notifyReporterIfOnline(report);
                } catch (SQLException e) {
                    plugin.getLogger().warning("Error updating report after warn: " + e.getMessage());
                }

                // Veritabanına uyarı kaydı ekle (provider üzerinden)
                plugin.getPunishmentManager().getProvider().warn(
                    targetName,
                    finalReason,
                    staff.getName()
                );
            }
        );
    }

    /**
     * Remove Punishment - Cezayı kaldır (Unban/Unmute)
     */
    private void handleRemovePunishment(Player staff, Report report) {
        String targetName = report.getReportedPlayerName();
        staff.closeInventory();

        // Önce ban ve mute durumunu kontrol et
        boolean isBanned = plugin.getPunishmentManager().getProvider().isBanned(targetName);
        boolean isMuted = plugin.getPunishmentManager().getProvider().isMuted(targetName);

        if (!isBanned && !isMuted) {
            plugin.getMessageManager().sendMessage(staff, "punishments.no-active-punishment", "%player%", targetName);
            plugin.getMessageManager().sendMessage(staff, "punishments.no-active-detail");
            return;
        }

        // Cezaları kaldır
        boolean unbanSuccess = false;
        boolean unmuteSuccess = false;

        if (isBanned) {
            unbanSuccess = plugin.getPunishmentManager().getProvider().unban(targetName);
            if (unbanSuccess) {
                plugin.getMessageManager().sendMessage(staff, "punishments.unban-success", "%player%", targetName);
            } else {
                plugin.getMessageManager().sendMessage(staff, "punishments.unban-failed", "%player%", targetName);
            }
        }

        if (isMuted) {
            unmuteSuccess = plugin.getPunishmentManager().getProvider().unmute(targetName);
            if (unmuteSuccess) {
                plugin.getMessageManager().sendMessage(staff, "punishments.unmute-success", "%player%", targetName);
            } else {
                plugin.getMessageManager().sendMessage(staff, "punishments.unmute-failed", "%player%", targetName);
            }
        }

        // Log
        if (unbanSuccess || unmuteSuccess) {
            plugin.getLogger().info("[PunishmentRemoval] " + staff.getName() + " removed punishment(s) for " + targetName);
            staff.sendMessage("");
            plugin.getMessageManager().sendMessage(staff, "punishments.operation-complete");
        }
    }

    private void handleReplayControlGUI(Player player, ReplayControlGUI gui, int slot, boolean isLeftClick) {
        // ReplayControlGUI handling - delegate to the GUI's own handler if needed
        // For now, just close or do nothing as replay controls are handled by hotbar items
        player.closeInventory();
    }

    /**
     * Rapor kabul edildiğinde raporcu'ya bildirim gönderir (Hypixel tarzı)
     * Raporcu online ise anında gönderir, offline ise giriş yaptığında gönderilir
     */
    private void notifyReporterIfOnline(Report report) {
        // Trust level cache'ini invalidate et (raporlanan oyuncu için)
        if (plugin.getTrustLevelManager() != null && report.getReportedPlayerName() != null) {
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(report.getReportedPlayerName());
            if (target.getUniqueId() != null) {
                plugin.getTrustLevelManager().invalidateCache(target.getUniqueId());
            }
        }

        if (!plugin.getConfig().getBoolean("reports.reporter-feedback.enabled", true)) return;

        String reporterUuid = report.getReporterUuid();
        if (reporterUuid == null || reporterUuid.isEmpty()) return;

        try {
            Player reporter = Bukkit.getPlayer(UUID.fromString(reporterUuid));
            if (reporter != null && reporter.isOnline()) {
                plugin.getMessageManager().sendReporterFeedback(reporter, report.getId(), report.getReportedPlayerName());
                plugin.getReportService().markReporterNotified(report.getId());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Reporter feedback gönderilemedi: " + e.getMessage());
        }
    }

}