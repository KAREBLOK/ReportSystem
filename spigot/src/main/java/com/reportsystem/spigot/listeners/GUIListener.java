package com.reportsystem.spigot.listeners;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.gui.*;
import com.reportsystem.spigot.punishment.ReportResolution;
import org.bukkit.Bukkit;
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

import java.util.Locale;
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

    /**
     * GÜVENLİK yardımcısı: yetkili aksiyonlarını izinle kapılar.
     * İzin yoksa uyarı gönderip envanteri kapatır ve false döner.
     */
    private boolean requirePermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        player.closeInventory();
        plugin.getMessageManager().sendNoPermission(player);
        return false;
    }

    private void handleReportListGUI(Player player, ReportListGUI gui, int slot) {
        // Savunma-derinliği: liste zaten view.all ile kapılı ama detay/ceza yüzeyine
        // geçişten önce de doğrula (GUI başka yoldan açılırsa diye).
        if (!player.hasPermission("reportsystem.view.all")) {
            requirePermission(player, "reportsystem.view.all");
            return;
        }
        if (slot == 48 && gui.getCurrentPage() > 1) {
            new ReportListGUI(plugin, player, gui.getCurrentPage() - 1).open();
        } else if (slot == 50 && gui.getCurrentPage() < gui.getMaxPage()) {
            new ReportListGUI(plugin, player, gui.getCurrentPage() + 1).open();
        } else if (slot == 45) {
            gui.refresh();
        } else {
            Report report = gui.getReportAtSlot(slot);
            if (report != null) {
                ReportDetailGUI.openAsync(plugin, player, report);
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
            // Raporu Onayla - Ceza seçim GUI'sini aç (yetkili aksiyonu)
            if (!requirePermission(player, "reportsystem.manage")) return;
            acceptReportAndShowPunishmentSelection(player, report);
        } else if (slot == config.getItemSlot("actions.reject")) {
            // Raporu Reddet (yetkili aksiyonu)
            if (!requirePermission(player, "reportsystem.manage")) return;
            rejectReport(player, report);
        } else if (slot == config.getItemSlot("punishments.teleport")) {
            // Oyuncuya Işınlan (yetkili aksiyonu)
            if (!requirePermission(player, "reportsystem.manage")) return;
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
        // GÜVENLİK (CMD-02): ceza aksiyonu, yetki aksiyon noktasında yeniden kontrol edilir
        if (!requirePermission(staff, "reportsystem.punish")) return;

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // İptal veya geri butonu
        if (item.getType() == Material.RED_CONCRETE) {
            staff.closeInventory();
            return;
        }
        if (item.getType() == Material.ARROW) {
            ReportDetailGUI.openAsync(plugin, staff, gui.getReport());
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
                            // Sohbet girişi beklenirken yetki alınmış olabilir (CMD-15)
                            if (!staff.hasPermission("reportsystem.punish")) {
                                plugin.getMessageManager().sendNoPermission(staff);
                                return;
                            }
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
        // GÜVENLİK (CMD-02/CMD-15): sohbet geri çağrısından da gelinebilir → yetkiyi yeniden doğrula
        if (!staff.hasPermission("reportsystem.punish")) {
            plugin.getMessageManager().sendNoPermission(staff);
            return;
        }
        if (report == null) {
            return;
        }

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

        // Hedef oyuncuyu bul (CRA-02: tam ad eşleşmesi; önek eşleşmesi yanlış oyuncuyu banlardı)
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            plugin.getMessageManager().sendMessage(staff, "punishments.ban.offline", "%player%", targetName);
            return;
        }

        final UUID staffId = staff.getUniqueId();
        final String staffName = staff.getName();
        final int reportId = report.getId();
        final String durationTextFinal = durationText;

        // ÇİFT-CEZA KORUMASI (DATA-03/DB-21): cezadan ÖNCE raporu atomik olarak talep et
        ReportResolution.claim(plugin, reportId, Report.Status.ACCEPTED, result -> {
            Player staffNow = Bukkit.getPlayer(staffId);
            if (result == ReportResolution.ClaimResult.ALREADY_HANDLED) {
                if (staffNow != null) {
                    plugin.getMessageManager().sendMessage(staffNow, "reports.actions.already-handled");
                }
                return;
            }
            if (result == ReportResolution.ClaimResult.ERROR) {
                if (staffNow != null) {
                    plugin.getMessageManager().sendMessage(staffNow, "reports.actions.database-error");
                }
                return;
            }

            java.util.function.Consumer<Boolean> onResult = ok ->
                    onBanResult(report, reportId, staffId, staffName, targetName, finalReason, durationTextFinal, ok);

            Player t2 = Bukkit.getPlayerExact(targetName);
            if (t2 != null && plugin.getAnimatedBanManager() != null) {
                // Animasyonlu ban başlat — rapor/webhook yalnız ban gerçekten uygulanınca (onResult)
                plugin.getAnimatedBanManager().executeAnimatedBan(t2, finalReason, staffName, durationMillis, onResult);

                if (staffNow != null) {
                    plugin.getMessageManager().sendMessage(staffNow, "punishments.ban.animated-started");
                    plugin.getMessageManager().sendMessage(staffNow, "punishments.ban.animated-target", "%player%", targetName);
                    plugin.getMessageManager().sendMessage(staffNow, "punishments.ban.animated-duration", "%duration%", durationTextFinal);
                    plugin.getMessageManager().sendMessage(staffNow, "punishments.ban.animated-reason", "%reason%", finalReason);
                    plugin.getMessageManager().sendMessage(staffNow, "punishments.ban.animated-message");
                }
            } else {
                // Talep sırasında hedef çıktı → animasyonsuz doğrudan ban
                boolean ok;
                try {
                    ok = plugin.getPunishmentManager().getProvider().ban(targetName, finalReason, staffName, durationMillis);
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "[AnimatedBan] Direct ban failed for " + targetName, e);
                    ok = false;
                }
                onResult.accept(ok);
            }
        });
    }

    /**
     * Animasyonlu banın gerçek sonucu (ana thread). Rapor ceza türü, Overwatch, webhook ve
     * raporcu bildirimi YALNIZ ban gerçekten uygulandıysa; değilse rapor tekrar PENDING'e alınır.
     */
    private void onBanResult(Report report, int reportId, UUID staffId, String staffName, String targetName,
                             String finalReason, String durationText, boolean ok) {
        Player staffNow = Bukkit.getPlayer(staffId);
        if (!ok) {
            ReportResolution.revert(plugin, reportId, Report.Status.ACCEPTED);
            if (staffNow != null) {
                if (plugin.getPunishmentManager().isUnknownBanTarget(targetName)) {
                    plugin.getMessageManager().sendMessage(staffNow, "punishments.ban.unknown-player", "%player%", targetName);
                } else {
                    plugin.getMessageManager().sendMessage(staffNow, "punishments.failed");
                }
            }
            return;
        }

        // Yalnız webhook/bildirim nesnesi için (DB'ye updateReport ile bayat nesne yazılmaz)
        report.setStatus(Report.Status.ACCEPTED);
        report.setResolvedBy(staffName);
        report.setResolvedAt(System.currentTimeMillis());
        report.setPunished(true);
        report.setPunishmentType("BAN");

        // Ceza türü + Overwatch oylamasını sonlandır (SUÇLU) — async
        ReportResolution.finishAsync(plugin, reportId, "BAN", true, staffName);

        // Eklenti kapanırken (onDisable'da uygulanan bekleyen ban) webhook/bildirim zamanlanamaz
        if (!plugin.isEnabled()) {
            return;
        }

        // Send single Discord webhook notification (report accepted + punishment applied)
        if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
            plugin.getWebhookManager().sendReportClosedWithPunishmentNotification(
                    report,
                    staffName,
                    "BAN",
                    finalReason,
                    durationText
            );
        }
        // Raporcu'ya bildirim
        notifyReporterIfOnline(report);

        if (staffNow != null) {
            plugin.getMessageManager().sendMessage(staffNow, "punishments.success-target", "%player%", targetName);
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
        // GÜVENLİK (CMD-02): ceza aksiyonu, yetki aksiyon noktasında yeniden kontrol edilir
        if (!requirePermission(player, "reportsystem.punish")) return;

        // Rapor olmadan ceza GUI'si açılmaz (tek oluşturucu PunishmentSelectionGUI her zaman rapor verir)
        Report report = gui.getReport();
        if (report == null) {
            player.closeInventory();
            return;
        }

        plugin.debug("[PunishmentGUI] Item clicked: " + item.getType());

        if (item.getType() == Material.RED_CONCRETE || item.getType() == Material.ARROW) {
            player.closeInventory();
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            plugin.debug("[PunishmentGUI] Meta is null!");
            return;
        }

        // Use PersistentDataContainer to get duration
        NamespacedKey durationKey = new NamespacedKey(plugin, "punishment_duration");
        String durationString = meta.getPersistentDataContainer().get(durationKey, PersistentDataType.STRING);

        plugin.debug("[PunishmentGUI] Duration from PDC: " + durationString);

        if (durationString == null || durationString.isEmpty()) {
            return;
        }

        player.closeInventory();
        String targetName = gui.getTargetName();
        String punishmentType = gui.getPunishmentType();
        if (punishmentType == null) {
            return;
        }
        String typeLower = punishmentType.toLowerCase(Locale.ROOT);
        String typeUpper = punishmentType.toUpperCase(Locale.ROOT);

        plugin.debug("[PunishmentGUI] Duration: " + durationString + ", Target: " + targetName + ", Type: " + typeUpper);

        // Raporun sebebini kullan ve direkt uygula
        String finalReason = report.getReason();
        long durationMillis = plugin.getPunishmentManager().parseDuration(durationString);

        // O9: hatalı (ayrıştırılamayan) yml süresi sessizce kalıcı cezaya dönüşmesin
        if (!durationString.equalsIgnoreCase("permanent") && durationMillis <= 0) {
            plugin.getMessageManager().sendMessage(player, "reports.chat.invalid-duration");
            return;
        }

        // Kick çevrimiçi hedef ister — talepten önce kontrol (talep yok)
        if (typeLower.equals("kick") && Bukkit.getPlayerExact(targetName) == null) {
            plugin.getMessageManager().sendMessage(player, "punishments.kick.offline", "%player%", targetName);
            return;
        }

        final UUID staffId = player.getUniqueId();
        final String staffName = player.getName();
        final int reportId = report.getId();

        // ÇİFT-CEZA KORUMASI (DATA-03/DB-21): cezadan ÖNCE raporu atomik olarak talep et
        ReportResolution.claim(plugin, reportId, Report.Status.ACCEPTED, result -> {
            Player staffNow = Bukkit.getPlayer(staffId);
            if (result == ReportResolution.ClaimResult.ALREADY_HANDLED) {
                if (staffNow != null) {
                    plugin.getMessageManager().sendMessage(staffNow, "reports.actions.already-handled");
                }
                return;
            }
            if (result == ReportResolution.ClaimResult.ERROR) {
                if (staffNow != null) {
                    plugin.getMessageManager().sendMessage(staffNow, "reports.actions.database-error");
                }
                return;
            }

            boolean success = false;
            try {
                if (typeLower.equals("ban")) {
                    success = plugin.getPunishmentManager().getProvider().ban(targetName, finalReason, staffName, durationMillis);
                } else if (typeLower.equals("mute")) {
                    success = plugin.getPunishmentManager().getProvider().mute(targetName, finalReason, staffName, durationMillis);
                } else if (typeLower.equals("kick")) {
                    Player targetPlayer = Bukkit.getPlayerExact(targetName);
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        String kickMsg = plugin.getMessageManager().colorize(
                                plugin.getMessageManager().getMessage("punishments.kick.player-message")
                                        .replace("%reason%", finalReason)
                                        .replace("%staff%", staffName));
                        targetPlayer.kickPlayer(kickMsg);
                        success = true;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "[PunishmentGUI] Punishment provider error (" + typeUpper + " " + targetName + ")", e);
                success = false;
            }

            if (!success) {
                // Ceza uygulanamadı → rapor tekrar PENDING (yetkili tekrar deneyebilir)
                ReportResolution.revert(plugin, reportId, Report.Status.ACCEPTED);
                if (staffNow != null) {
                    if (typeLower.equals("ban") && plugin.getPunishmentManager().isUnknownBanTarget(targetName)) {
                        plugin.getMessageManager().sendMessage(staffNow, "punishments.ban.unknown-player", "%player%", targetName);
                    } else {
                        plugin.getMessageManager().sendMessage(staffNow, "punishments.failed");
                    }
                }
                return;
            }

            // Yalnız webhook nesnesi için (DB'ye bayat nesne yazılmaz)
            report.setStatus(Report.Status.ACCEPTED);
            report.setResolvedBy(staffName);
            report.setResolvedAt(System.currentTimeMillis());
            report.setPunished(true);
            report.setPunishmentType(typeUpper);

            // Ceza türü + Overwatch oylamasını sonlandır (SUÇLU) — async
            ReportResolution.finishAsync(plugin, reportId, typeUpper, true, staffName);

            if (staffNow != null) {
                plugin.getMessageManager().sendMessage(staffNow, "punishments.success-target", "%player%", targetName);
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
                    report, staffName, typeUpper, finalReason, durationText
                );
            }
        });
    }

    // --- Yardımcı Metotlar ---

    /**
     * Ceza seçim GUI'sini göster (henüz rapor ACCEPTED olarak işaretlenmez)
     */
    private void acceptReportAndShowPunishmentSelection(Player staff, Report report) {
        plugin.getMessageManager().sendMessage(staff, "reports.actions.select-punishment");

        // Ceza seçim GUI'sini aç (rapor henüz ACCEPTED değil, ceza seçildiğinde işaretlenecek)
        new PunishmentSelectionGUI(staff, report, plugin).open();
    }

    private void rejectReport(Player staff, Report report) {
        staff.closeInventory();

        final UUID staffId = staff.getUniqueId();
        final String staffName = staff.getName();
        final int reportId = report.getId();

        // ÜZERİNE YAZMA KORUMASI (DATA-03/DB-21): yalnız hâlâ PENDING olan rapor reddedilir;
        // bayat nesneden "Reddet" artık kabul edilmiş (cezalı) raporu ezemez.
        ReportResolution.claim(plugin, reportId, Report.Status.REJECTED, result -> {
            Player staffNow = Bukkit.getPlayer(staffId);
            if (result == ReportResolution.ClaimResult.ALREADY_HANDLED) {
                if (staffNow != null) {
                    plugin.getMessageManager().sendMessage(staffNow, "reports.actions.already-handled");
                }
                return;
            }
            if (result == ReportResolution.ClaimResult.ERROR) {
                if (staffNow != null) {
                    plugin.getMessageManager().sendMessage(staffNow, "reports.actions.database-error");
                }
                return;
            }

            // Yalnız webhook nesnesi için
            report.setStatus(Report.Status.REJECTED);
            report.setResolvedBy(staffName);
            report.setResolvedAt(System.currentTimeMillis());

            // Overwatch oylamasını yetkili kararına göre sonlandır (MASUM) ve kuyruktan kaldır — async
            ReportResolution.finishAsync(plugin, reportId, null, false, staffName);

            // Send Discord webhook notification
            if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                plugin.getWebhookManager().sendReportClosedNotification(report, staffName, report.getResponse());
            }

            if (staffNow != null) {
                plugin.getMessageManager().sendMessage(staffNow, "reports.actions.rejected", "%id%", String.valueOf(reportId));
            }
        });
    }

    private void teleportToPlayer(Player staff, String targetName) {
        // H-47: replay izleyen / Overwatch incelemesindeki (post-replay dahil) yetkili buradan
        // ışınlanamaz — gizli + uçan izleyici durumundan dünyada gezinme/kaçış yolu olurdu.
        com.reportsystem.spigot.overwatch.listeners.OverwatchReplayListener owl = plugin.getOverwatchReplayListener();
        if (plugin.getReplayManager().isWatchingReplay(staff)
                || (owl != null && owl.needsProtection(staff.getUniqueId()))) {
            plugin.getMessageManager().sendMessage(staff, "replay.teleport-blocked");
            return;
        }
        // CRA-02: tam ad eşleşmesi (önek eşleşmesi yanlış oyuncuya ışınlardı)
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null && target.isOnline()) {
            staff.teleport(target);
            plugin.getMessageManager().sendMessage(staff, "general.teleported", "%player%", targetName);
        } else {
            plugin.getMessageManager().sendMessage(staff, "general.player-offline", "%player%", targetName);
        }
    }

    private void watchReplay(Player viewer, int reportId) {
        // Overwatch incelemesindeki (post-replay dahil) oyuncu buradan normal replay baslatamaz:
        // iki oturumun envanter/konum kayitlari cakisir (dupe yolu).
        com.reportsystem.spigot.overwatch.listeners.OverwatchReplayListener owl = plugin.getOverwatchReplayListener();
        if (owl != null && owl.isReviewing(viewer.getUniqueId())) {
            viewer.closeInventory();
            plugin.getMessageManager().sendMessage(viewer, "replay.already-watching");
            return;
        }
        viewer.closeInventory();
        plugin.getReplayManager().startReplay(reportId, viewer);
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
        if (!plugin.isEnabled()) return; // async görev zamanlanamaz (kapanış)

        final int reportId = report.getId();
        final String reportedName = report.getReportedPlayerName();

        // Trust level cache'ini invalidate et (raporlanan oyuncu için).
        // THR-01/CRA-03: getOfflinePlayer(name) bloklayan profil sorgusu yapabilir → async;
        // TrustLevelManager önbelleği ConcurrentHashMap (thread-safe).
        if (plugin.getTrustLevelManager() != null && reportedName != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(reportedName);
                    if (target.getUniqueId() != null && plugin.getTrustLevelManager() != null) {
                        plugin.getTrustLevelManager().invalidateCache(target.getUniqueId());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Trust cache invalidation failed: " + e.getMessage());
                }
            });
        }

        if (!plugin.getConfig().getBoolean("reports.reporter-feedback.enabled", true)) return;

        String reporterUuid = report.getReporterUuid();
        if (reporterUuid == null || reporterUuid.isEmpty()) return;

        try {
            Player reporter = Bukkit.getPlayer(UUID.fromString(reporterUuid));
            if (reporter != null && reporter.isOnline()) {
                // Mesaj ana thread'de; DB yazımı (THR-01) async
                plugin.getMessageManager().sendReporterFeedback(reporter, reportId, reportedName);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                        () -> plugin.getReportService().markReporterNotified(reportId));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Reporter feedback gönderilemedi: " + e.getMessage());
        }
    }

}