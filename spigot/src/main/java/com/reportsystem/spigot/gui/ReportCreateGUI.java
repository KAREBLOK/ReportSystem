package com.reportsystem.spigot.gui;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.commands.ReportCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.concurrent.CompletableFuture;

public class ReportCreateGUI extends GUI {
    private final String targetName;
    private final boolean isTargetOnline;
    private final GUIConfig guiConfig;

    public ReportCreateGUI(ReportSystemSpigot plugin, Player player, String targetName) {
        super(plugin, player);
        this.targetName = targetName;
        this.isTargetOnline = Bukkit.getPlayer(targetName) != null;
        this.guiConfig = new GUIConfig(plugin, "report-create");
    }

    @Override
    public void open() {
        build();
        player.openInventory(inventory);

        // Play open sound from config
        try {
            Sound sound = Sound.valueOf(guiConfig.getSound("open"));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception e) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    @Override
    public void build() {
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

        // Create inventory with title from config
        String title = guiConfig.getTitle(
                "%player%", targetName,
                "%server%", spigotPlugin.getServerName()
        );
        int size = guiConfig.getSize();
        inventory = Bukkit.createInventory(this, size, title);

        // Background
        if (guiConfig.isBackgroundEnabled()) {
            ItemStack bgItem = guiConfig.getBackgroundItem();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, bgItem);
            }
        }

        // Player head
        ItemStack playerHead = guiConfig.getItem("player-head",
                "%player%", targetName,
                "%server%", spigotPlugin.getServerName(),
                "%online_status%", plugin.getMessageManager().getMessage(isTargetOnline ? "gui.report.online" : "gui.report.offline")
        );

        if (playerHead != null && playerHead.getType() == Material.PLAYER_HEAD) {
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwner(targetName);
                playerHead.setItemMeta(skullMeta);
            }
            int slot = guiConfig.getItemSlot("player-head");
            inventory.setItem(slot, playerHead);
        }

        // Report reasons
        addReasonItem("reasons.cheating");
        addReasonItem("reasons.chat-abuse");
        addReasonItem("reasons.griefing");
        addReasonItem("reasons.bug-abuse");
        addReasonItem("reasons.teaming");

        // Custom reason
        addCustomReasonItem();

        // Close button
        ItemStack closeItem = guiConfig.getItem("close");
        if (closeItem != null) {
            int slot = guiConfig.getItemSlot("close");
            inventory.setItem(slot, closeItem);
        }

        // Info item (optional)
        ItemStack infoItem = guiConfig.getItem("info",
                "%cooldown%", String.valueOf(spigotPlugin.getConfigManager().getReportCooldown())
        );
        if (infoItem != null) {
            int slot = guiConfig.getItemSlot("info");
            inventory.setItem(slot, infoItem);
        }
    }

    private void addReasonItem(String configPath) {
        ItemStack item = guiConfig.getItem(configPath);
        if (item != null) {
            int slot = guiConfig.getItemSlot(configPath);
            inventory.setItem(slot, item);
        }
    }

    private void addCustomReasonItem() {
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
        ItemStack item = guiConfig.getItem("custom-reason",
                "%min_length%", String.valueOf(spigotPlugin.getConfigManager().getMinReasonLength()),
                "%max_length%", String.valueOf(spigotPlugin.getConfigManager().getMaxReasonLength())
        );
        if (item != null) {
            int slot = guiConfig.getItemSlot("custom-reason");
            inventory.setItem(slot, item);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        String reason = null;

        // Check config for reason slots
        if (slot == guiConfig.getItemSlot("reasons.cheating")) {
            reason = plugin.getMessageManager().getMessage("gui.report-create.reasons.cheating");
        } else if (slot == guiConfig.getItemSlot("reasons.chat-abuse")) {
            reason = plugin.getMessageManager().getMessage("gui.report-create.reasons.chat-abuse");
        } else if (slot == guiConfig.getItemSlot("reasons.griefing")) {
            reason = plugin.getMessageManager().getMessage("gui.report-create.reasons.griefing");
        } else if (slot == guiConfig.getItemSlot("reasons.bug-abuse")) {
            reason = plugin.getMessageManager().getMessage("gui.report-create.reasons.bug-abuse");
        } else if (slot == guiConfig.getItemSlot("reasons.teaming")) {
            reason = plugin.getMessageManager().getMessage("gui.report-create.reasons.teaming");
        } else if (slot == guiConfig.getItemSlot("custom-reason")) {
            player.closeInventory();
            requestCustomReason();
            return;
        } else if (slot == guiConfig.getItemSlot("close")) {
            player.closeInventory();
            playSound("close");
            return;
        }

        if (reason != null) {
            player.closeInventory();
            playSound("select-reason");
            createReport(reason);
        }
    }

    private void requestCustomReason() {
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

        player.sendMessage("");
        player.sendMessage("§8§m                                                     ");
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("gui.report.enter-reason")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("gui.report.cancel-instruction")));
        player.sendMessage("§8§m                                                     ");
        player.sendMessage("");

        // Chat input bekle
        String enterReasonMsg = plugin.getMessageManager().getMessage("gui.report.enter-reason");
        spigotPlugin.getChatInputManager().requestInput(player, enterReasonMsg, input -> {
            int minLength = spigotPlugin.getConfigManager().getMinReasonLength();
            int maxLength = spigotPlugin.getConfigManager().getMaxReasonLength();

            if (input.length() < minLength) {
                String errorMsg = plugin.getMessageManager().getMessage("reports.reason-too-short");
                errorMsg = errorMsg.replace("%min%", String.valueOf(minLength));
                player.sendMessage(plugin.getMessageManager().colorize(errorMsg));
                open();
                return;
            }

            if (input.length() > maxLength) {
                String errorMsg = plugin.getMessageManager().getMessage("reports.reason-too-long");
                errorMsg = errorMsg.replace("%max%", String.valueOf(maxLength));
                player.sendMessage(plugin.getMessageManager().colorize(errorMsg));
                open();
                return;
            }

            createReport(input);
        });
    }

    private void createReport(String reason) {
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

        // Custom reasons are already validated in requestCustomReason()
        // Predefined reasons are admin-approved and don't need validation

        String creatingMsg = plugin.getMessageManager().getMessage("gui.report.creating");
        player.sendMessage(plugin.getMessageManager().colorize(creatingMsg));

        CompletableFuture.supplyAsync(() -> {
            try {
                // Report oluştur (limit kontrolü ile)
                int maxReportsPerPlayer = spigotPlugin.getConfigManager().getMaxReportsPerPlayer();
                return spigotPlugin.getReportService().createReport(
                        player.getName(),
                        player.getUniqueId().toString(),
                        targetName,
                        "", // targetUuid - bilinmiyor
                        reason,
                        spigotPlugin.getServerName(),
                        maxReportsPerPlayer
                );
            } catch (Exception e) {
                spigotPlugin.getLogger().severe("Rapor oluşturulurken hata: " + e.getMessage());
                return CompletableFuture.completedFuture(-1);
            }
        }).thenAccept(futureResult -> {
            futureResult.thenAccept(result -> {
                Integer reportId = result;
                if (reportId != null && reportId > 0) {
                    spigotPlugin.getServer().getScheduler().runTask(spigotPlugin, () -> {
                        // Başarı mesajı
                        spigotPlugin.getMessageManager().sendReportSuccess(player, targetName, reportId);
                        playSound("select-reason");

                        // Hedef oyuncu online ise kayıt başlat
                        Player target = Bukkit.getPlayer(targetName);
                        if (target != null && target.isOnline()) {
                            // Zaten kayıt yoksa başlat
                            if (!spigotPlugin.getRecordingManager().isRecording(target.getUniqueId())) {
                                int duration = spigotPlugin.getConfigManager().getRecordingDuration();
                                spigotPlugin.getRecordingManager().startRecording(target, reportId, duration, player.getName(), reason);
                                spigotPlugin.getMessageManager().sendRecordingStarted(player);
                            } else {
                                // Zaten kayıt varsa sadece ID'yi güncelle
                                ReportCommand.updateRecordingWithReportId(target.getUniqueId(), reportId);
                            }
                        }

                        // Otomatik olarak Overwatch kuyruğuna ekle (sadece PENDING raporlar için)
                        if (spigotPlugin.getOverwatchManager() != null) {
                            spigotPlugin.getOverwatchManager().addReportToQueue(reportId, 5); // Öncelik 5 (normal)
                        }

                        // NOT: Staff bildirimi ve webhook bildirimi kayıt bitince gönderilecek (RecordingManager'da)
                    });
                } else if (reportId != null && reportId == -2) {
                    // Limit aşıldı
                    spigotPlugin.getServer().getScheduler().runTask(spigotPlugin, () -> {
                        int maxLimit = spigotPlugin.getConfigManager().getMaxReportsPerPlayer();
                        spigotPlugin.getMessageManager().sendReportLimitReached(player, targetName, maxLimit);
                        playSound("error");
                    });
                } else {
                    spigotPlugin.getServer().getScheduler().runTask(spigotPlugin, () -> {
                        String errorMsg = plugin.getMessageManager().getMessage("gui.report.create-error");
                        player.sendMessage(plugin.getMessageManager().colorize(errorMsg));
                        playSound("error");
                    });
                }
            });
        });
    }

    private void playSound(String action) {
        try {
            Sound sound = Sound.valueOf(guiConfig.getSound(action));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception e) {
            // Fallback sounds
            if (action.equals("error")) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } else {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        }
    }

    public String getTargetName() {
        return targetName;
    }
}
