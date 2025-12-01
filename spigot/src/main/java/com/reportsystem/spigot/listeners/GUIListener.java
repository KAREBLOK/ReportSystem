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

        if (slot == 22) {
            // Replay İzle
            if (gui.hasReplay()) {
                watchReplay(player, report.getId());
            } else {
                player.sendMessage(ChatColor.RED + "Bu rapor için replay kaydı bulunamadı!");
            }
        } else if (slot == 29) {
            // Raporu Onayla - Ceza seçim GUI'sini aç
            acceptReportAndShowPunishmentSelection(player, report);
        } else if (slot == 30) {
            // Raporu Reddet
            rejectReport(player, report);
        } else if (slot == 42) {
            // Oyuncuya Işınlan
            player.closeInventory();
            teleportToPlayer(player, report.getReportedPlayerName());
        } else if (slot == 49) {
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

                plugin.getLogger().info("[DEBUG] AnimatedBanGUI click - Duration: " + duration + ", Target: " + targetName);

                staff.closeInventory();

                // Özel süre mi?
                if (duration.equals("custom")) {
                    // Kullanıcıdan özel süre al
                    plugin.getChatInputManager().requestInput(staff,
                        ChatColor.GOLD + "Ban süresini girin (örn: 2h, 15d, 1w):",
                        customDuration -> {
                            if (customDuration == null || customDuration.trim().isEmpty()) {
                                staff.sendMessage(ChatColor.RED + "Geçersiz süre! İşlem iptal edildi.");
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
                plugin.getLogger().warning("[DEBUG] AnimatedBanGUI click - No ban_duration data! Item: " + item.getType());
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
            durationText = "KALICI";
        } else {
            durationMillis = plugin.getPunishmentManager().parseDuration(duration);
            if (durationMillis <= 0) {
                staff.sendMessage(ChatColor.RED + "Geçersiz süre formatı! İşlem iptal edildi.");
                return;
            }
            durationText = formatDuration(durationMillis);
        }

        // Hedef oyuncuyu bul
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            staff.sendMessage(ChatColor.RED + targetName + " çevrimiçi değil! Animasyonlu ban için oyuncunun online olması gerekiyor.");
            return;
        }

        // Animasyonlu ban başlat
        plugin.getAnimatedBanManager().executeAnimatedBan(target, finalReason, staff, durationMillis);

        // Başarı mesajı
        staff.sendMessage(ChatColor.GREEN + "⚡ " + ChatColor.BOLD + "ANIMATED BAN BAŞLATILDI!");
        staff.sendMessage(ChatColor.GRAY + "Hedef: " + ChatColor.RED + targetName);
        staff.sendMessage(ChatColor.GRAY + "Süre: " + ChatColor.YELLOW + durationText);
        staff.sendMessage(ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + finalReason);
        staff.sendMessage(ChatColor.DARK_RED + "Tanrıların gazabı başlıyor...");

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

        if (days > 0) return days + " gün";
        if (hours > 0) return hours + " saat";
        if (minutes > 0) return minutes + " dakika";
        return seconds + " saniye";
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
            String defaultReason = gui.getReport() != null ? gui.getReport().getReason() : "Kural ihlali";
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
                        targetPlayer.kickPlayer(ChatColor.RED + "Sunucudan atıldınız!\n\n" +
                                ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + finalReason + "\n" +
                                ChatColor.GRAY + "Yetkili: " + ChatColor.WHITE + player.getName());
                        success = true;
                    }
                }

                if (success) {
                    player.sendMessage(ChatColor.GREEN + "✓ " + targetName + " için ceza başarıyla uygulandı.");

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
                    player.sendMessage(ChatColor.RED + "✗ Ceza uygulanamadı.");
                }
            } else {
                // Rapor yoksa chat input iste
                plugin.getChatInputManager().requestInput(player, "Ceza sebebini girin (varsayılan: " + defaultReason + ")", reason -> {
                    String finalReason = (reason == null || reason.isEmpty()) ? defaultReason : reason;
                    long durationMillis = plugin.getPunishmentManager().parseDuration(durationString);

                    boolean success = false;
                    if (gui.getPunishmentType().equalsIgnoreCase("ban")) {
                        success = plugin.getPunishmentManager().getProvider().ban(targetName, finalReason, player.getName(), durationMillis);
                    } else if (gui.getPunishmentType().equalsIgnoreCase("mute")) {
                        success = plugin.getPunishmentManager().getProvider().mute(targetName, finalReason, player.getName(), durationMillis);
                    }

                    if (success) {
                        player.sendMessage(ChatColor.GREEN + targetName + " için ceza başarıyla uygulandı.");
                    } else {
                        player.sendMessage(ChatColor.RED + "Ceza uygulanamadı.");
                    }
                });
            }
        }
    }

    // --- Yardımcı Metotlar ---

    private void createReport(Player reporter, String targetName, String reason) {
        reporter.closeInventory();
        if (reason.length() < plugin.getConfigManager().getMinReasonLength()) {
            reporter.sendMessage(ChatColor.RED + "Sebep çok kısa!");
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

        staff.sendMessage(ChatColor.YELLOW + "Lütfen bir ceza seçin veya GUI'yi kapatın.");

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
            staff.sendMessage(ChatColor.RED + "Rapor #" + report.getId() + " reddedildi.");
        } catch (SQLException e) {
            staff.sendMessage(ChatColor.RED + "Rapor güncellenirken bir veritabanı hatası oluştu.");
            e.printStackTrace();
        }
    }

    private void deleteReport(Player staff, Report report) {
        plugin.getReportService().deleteReport(report.getId());
        plugin.getReplayManager().deleteReplay(report.getId());
        staff.closeInventory();
        staff.sendMessage(ChatColor.GREEN + "Rapor #" + report.getId() + " ve ilgili replay silindi.");
    }

    private void teleportToPlayer(Player staff, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.isOnline()) {
            staff.teleport(target);
            staff.sendMessage(ChatColor.GREEN + targetName + " adlı oyuncuya ışınlandınız.");
        } else {
            staff.sendMessage(ChatColor.RED + targetName + " adlı oyuncu çevrimiçi değil.");
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
            staff.sendMessage(ChatColor.RED + targetName + " çevrimiçi değil!");
            return;
        }

        staff.closeInventory();

        // Sebep girişi al
        String defaultReason = report.getReason();
        plugin.getChatInputManager().requestInput(staff,
            ChatColor.GOLD + "Kick sebebini girin (varsayılan: " + defaultReason + ")",
            reason -> {
                String finalReason = (reason == null || reason.isEmpty()) ? defaultReason : reason;

                // Kick mesajı
                String kickMessage = ChatColor.RED + "" + ChatColor.BOLD + "SUNUCUDAN ATILDINIZ!\n\n" +
                    ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + finalReason + "\n" +
                    ChatColor.GRAY + "Yetkili: " + ChatColor.WHITE + staff.getName();

                target.kickPlayer(kickMessage);

                // Başarı mesajı
                staff.sendMessage(ChatColor.GREEN + targetName + " sunucudan atıldı!");
                staff.sendMessage(ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + finalReason);

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
            staff.sendMessage(ChatColor.RED + targetName + " çevrimiçi değil!");
            return;
        }

        staff.closeInventory();

        // Sebep girişi al
        String defaultReason = report.getReason();
        plugin.getChatInputManager().requestInput(staff,
            ChatColor.GOLD + "Uyarı mesajını girin (varsayılan: " + defaultReason + ")",
            reason -> {
                String finalReason = (reason == null || reason.isEmpty()) ? defaultReason : reason;

                // Uyarı mesajı gönder
                target.sendMessage("");
                target.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "⚠ UYARI ⚠");
                target.sendMessage(ChatColor.YELLOW + "Yetkili tarafından uyarıldınız!");
                target.sendMessage("");
                target.sendMessage(ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + finalReason);
                target.sendMessage(ChatColor.GRAY + "Yetkili: " + ChatColor.WHITE + staff.getName());
                target.sendMessage(ChatColor.YELLOW + "Kuralları ihlal etmeye devam ederseniz ceza alabilirsiniz!");
                target.sendMessage("");

                // Ses efekti
                target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);

                // Başarı mesajı
                staff.sendMessage(ChatColor.GREEN + targetName + " uyarıldı!");
                staff.sendMessage(ChatColor.GRAY + "Mesaj: " + ChatColor.WHITE + finalReason);

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
            staff.sendMessage(ChatColor.RED + targetName + " için aktif bir ceza bulunamadı!");
            staff.sendMessage(ChatColor.GRAY + "Ban veya mute cezası yok.");
            return;
        }

        // Cezaları kaldır
        boolean unbanSuccess = false;
        boolean unmuteSuccess = false;

        if (isBanned) {
            unbanSuccess = plugin.getPunishmentManager().getProvider().unban(targetName);
            if (unbanSuccess) {
                staff.sendMessage(ChatColor.GREEN + "✓ " + targetName + " banı kaldırıldı!");
            } else {
                staff.sendMessage(ChatColor.RED + "✗ " + targetName + " banı kaldırılırken hata oluştu!");
            }
        }

        if (isMuted) {
            unmuteSuccess = plugin.getPunishmentManager().getProvider().unmute(targetName);
            if (unmuteSuccess) {
                staff.sendMessage(ChatColor.GREEN + "✓ " + targetName + " susturması kaldırıldı!");
            } else {
                staff.sendMessage(ChatColor.RED + "✗ " + targetName + " susturması kaldırılırken hata oluştu!");
            }
        }

        // Log
        if (unbanSuccess || unmuteSuccess) {
            plugin.getLogger().info("[PunishmentRemoval] " + staff.getName() + " removed punishment(s) for " + targetName);
            staff.sendMessage("");
            staff.sendMessage(ChatColor.GRAY + "İşlem tamamlandı!");
        }
    }

    private void handleReplayControlGUI(Player player, ReplayControlGUI gui, int slot, boolean isLeftClick) {
        // ReplayControlGUI handling - delegate to the GUI's own handler if needed
        // For now, just close or do nothing as replay controls are handled by hotbar items
        player.closeInventory();
    }

}