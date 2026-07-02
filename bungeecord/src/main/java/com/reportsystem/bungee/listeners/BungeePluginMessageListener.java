package com.reportsystem.bungee.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.bungee.ReportSystemBungee;
import com.reportsystem.common.models.Report;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BungeePluginMessageListener implements Listener {

    private final ReportSystemBungee plugin;

    public BungeePluginMessageListener(ReportSystemBungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals("reportsystem:channel")) {
            return;
        }

        if (!(event.getSender() instanceof Server)) {
            return;
        }

        Server connection = (Server) event.getSender();
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());

        String subchannel = in.readUTF();

        // Otomatik olarak hangi sunucudan geldiğini al
        String serverName = connection.getInfo().getName();

        plugin.getLogger().info("[PLUGIN-MESSAGE] Received from " + serverName + ": " + subchannel);

        switch (subchannel) {
            case "CreateReport":
                handleCreateReport(connection, in, serverName);
                break;
            case "GlobalPunishment":
                handleGlobalPunishment(connection, in);
                break;
            case "GlobalUnpunishment":
                handleGlobalUnpunishment(connection, in);
                break;
            case "CheckPunishment":
                handleCheckPunishment(connection, in);
                break;
            case "CheckServerAndReplay":
                handleCheckServerAndReplay(connection, in);
                break;
        }
    }

    private void handleCreateReport(Server connection, ByteArrayDataInput in, String serverName) {
        String reporterName = in.readUTF();
        String reporterUUID = in.readUTF();
        String targetName = in.readUTF();
        String reason = in.readUTF();

        plugin.getLogger().info("[REPORT] Creating report from " + serverName + ": " +
                reporterName + " -> " + targetName);

        // Target oyuncunun UUID'sini bul
        String targetUUID = "";
        ProxiedPlayer targetPlayer = ProxyServer.getInstance().getPlayer(targetName);
        if (targetPlayer != null) {
            targetUUID = targetPlayer.getUniqueId().toString();
        } else {
            // Oyuncu online değilse dummy UUID kullan
            targetUUID = UUID.randomUUID().toString();
            plugin.getLogger().warning("[REPORT] Target player " + targetName + " is offline, using dummy UUID");
        }

        final String finalTargetUUID = targetUUID;

        // Limit kontrolü için config değerini al
        int maxReportsPerPlayer = plugin.getConfig().getInt("reports.max-reports-per-player", 3);

        // Report oluştur (async)
        plugin.getReportService().createReport(
                reporterName,            // reporterName
                reporterUUID,            // reporterUuid
                targetName,              // reportedPlayerName
                finalTargetUUID,         // reportedPlayerUuid
                reason,                  // reason
                serverName,              // serverName - artık otomatik alınıyor
                maxReportsPerPlayer      // maxReportsPerPlayer - spam koruması
        ).thenAccept(reportId -> {
            if (reportId > 0) {
                plugin.getLogger().info("[REPORT] Report created with ID #" + reportId +
                        " on server: " + serverName);

                // Asenkron işlemler için ProxyServer scheduler kullan
                ProxyServer.getInstance().getScheduler().schedule(plugin, () -> {
                    // Bildirimleri gönder
                    sendReportNotifications(reportId, reporterName, targetName, reason, serverName);

                    // Rapor oluşturan oyuncuya onay mesajı
                    ProxiedPlayer reporter = ProxyServer.getInstance().getPlayer(reporterName);
                    if (reporter != null) {
                        String createdMsg = ChatColor.translateAlternateColorCodes('&',
                                plugin.getConfig().getString("messages.report.created",
                                        "&a✓ Rapor başarıyla oluşturuldu! (ID: #%id%)"))
                                .replace("%id%", String.valueOf(reportId));
                        reporter.sendMessage(createdMsg);
                    }

                    // Target oyuncunun sunucusuna report ID'yi gönder
                    ProxiedPlayer target = ProxyServer.getInstance().getPlayer(targetName);
                    if (target != null && target.getServer() != null) {
                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeUTF("ReportCreated");
                        out.writeUTF(reporterName);
                        out.writeInt(reportId);
                        out.writeUTF(targetName);

                        target.getServer().sendData("reportsystem:channel", out.toByteArray());

                        plugin.getLogger().info("[REPORT] Sent report ID #" + reportId +
                                " to server: " + target.getServer().getInfo().getName());
                    }
                }, 0, TimeUnit.MILLISECONDS);
            } else if (reportId == -2) {
                // Limit aşıldı
                plugin.getLogger().warning("[REPORT] Report limit reached: " + reporterName + " -> " + targetName);

                ProxiedPlayer reporter = ProxyServer.getInstance().getPlayer(reporterName);
                if (reporter != null) {
                    String limitMsg = ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.report.limit-reached",
                                    "&c✗ %player% adlı oyuncuyu daha fazla rapor edemezsiniz! &7(Limit: %limit%)"))
                            .replace("%player%", targetName)
                            .replace("%limit%", String.valueOf(maxReportsPerPlayer));
                    reporter.sendMessage(limitMsg);
                }
            } else {
                plugin.getLogger().severe("[REPORT] Invalid report ID returned: " + reportId);

                ProxiedPlayer reporter = ProxyServer.getInstance().getPlayer(reporterName);
                if (reporter != null) {
                    reporter.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.report.error",
                                    "&cRapor oluşturulurken bir hata oluştu!")));
                }
            }

        }).exceptionally(ex -> {
            plugin.getLogger().severe("[REPORT] Report creation failed: " + ex.getMessage());

            ProxiedPlayer reporter = ProxyServer.getInstance().getPlayer(reporterName);
            if (reporter != null) {
                reporter.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.report.error",
                                "&cRapor oluşturulurken bir hata oluştu!")));
            }
            return null;
        });
    }

    private void handleCheckServerAndReplay(Server connection, ByteArrayDataInput in) {
        String playerName = in.readUTF();
        String reportServerName = in.readUTF();
        int reportId = in.readInt();

        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        if (player == null) {
            plugin.getLogger().warning("[REPLAY] Player not found: " + playerName);
            return;
        }

        String currentServer = player.getServer().getInfo().getName();

        plugin.getLogger().info("[REPLAY] Check server - Player: " + playerName +
                ", Current: " + currentServer + ", Report Server: " + reportServerName);

        // Aynı sunucuda mı kontrol et
        if (currentServer.equalsIgnoreCase(reportServerName)) {
            // Aynı sunucuda, direkt replay başlat
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("StartReplayDirect");
            out.writeUTF(playerName);
            out.writeInt(reportId);

            player.getServer().sendData("reportsystem:channel", out.toByteArray());

            plugin.getLogger().info("[REPLAY] Same server, starting replay directly on: " + currentServer);
        } else {
            // Farklı sunucu, önce oyuncuyu transfer et
            plugin.getLogger().info("[REPLAY] Different server, need to transfer from " +
                    currentServer + " to " + reportServerName);

            // Önce target sunucunun var olup olmadığını kontrol et
            if (ProxyServer.getInstance().getServerInfo(reportServerName) == null) {
                plugin.getLogger().severe("[REPLAY] Target server not found: " + reportServerName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.report.server-not-found",
                                "&cHedef sunucu bulunamadı: %server%"))
                        .replace("%server%", reportServerName));
                return;
            }

            // Transfer mesajını mevcut sunucuya gönder
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("TransferForReplay");
            out.writeUTF(playerName);
            out.writeUTF(reportServerName);
            out.writeInt(reportId);

            player.getServer().sendData("reportsystem:channel", out.toByteArray());

            plugin.getLogger().info("[REPLAY] Sent transfer request to current server");
        }
    }

    private void sendReportNotifications(int reportId, String reporterName, String targetName,
                                         String reason, String serverName) {
        // ReportNotificationListener'ı kullanarak tüm sunuculara broadcast gönder
        ReportNotificationListener notificationListener = plugin.getReportNotificationListener();
        if (notificationListener != null) {
            notificationListener.broadcastReportNotification(reportId, reporterName, targetName, reason, serverName);
        } else {
            plugin.getLogger().warning("[NOTIFICATION] ReportNotificationListener is null!");
        }
    }

    private void handleGlobalPunishment(Server connection, ByteArrayDataInput in) {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();
        String reason = in.readUTF();
        String punisher = in.readUTF();
        long duration = in.readLong();
        String serverOrigin = connection.getInfo().getName();

        plugin.getLogger().info("[PUNISHMENT] " + type + " request for " + playerName + " from " + serverOrigin);

        boolean success = false;

        switch (type) {
            case "ban":
                success = plugin.getPunishmentManager().globalBan(playerName, reason, punisher, duration, serverOrigin);
                break;
            case "mute":
                success = plugin.getPunishmentManager().globalMute(playerName, reason, punisher, duration, serverOrigin);
                break;
            case "kick":
                success = plugin.getPunishmentManager().globalKick(playerName, reason, punisher, serverOrigin);
                break;
        }

        // Sonucu geri gönder
        ByteArrayDataOutput response = ByteStreams.newDataOutput();
        response.writeUTF("PunishmentResult");
        response.writeUTF(requestId);
        response.writeBoolean(success);

        connection.sendData("reportsystem:channel", response.toByteArray());
    }

    private void handleGlobalUnpunishment(Server connection, ByteArrayDataInput in) {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();

        boolean success = false;

        switch (type) {
            case "unban":
                success = plugin.getPunishmentManager().removePunishment(playerName, "ban");
                break;
            case "unmute":
                success = plugin.getPunishmentManager().removePunishment(playerName, "mute");
                break;
        }

        ByteArrayDataOutput response = ByteStreams.newDataOutput();
        response.writeUTF("UnpunishmentResult");
        response.writeUTF(requestId);
        response.writeBoolean(success);

        connection.sendData("reportsystem:channel", response.toByteArray());
    }

    private void handleCheckPunishment(Server connection, ByteArrayDataInput in) {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();

        boolean result = plugin.getPunishmentManager().isActivePunishment(playerName, type);

        ByteArrayDataOutput response = ByteStreams.newDataOutput();
        response.writeUTF("CheckPunishmentResult");
        response.writeUTF(requestId);
        response.writeBoolean(result);

        connection.sendData("reportsystem:channel", response.toByteArray());
    }
}