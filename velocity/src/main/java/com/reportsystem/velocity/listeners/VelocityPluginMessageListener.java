package com.reportsystem.velocity.listeners;

import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.config.VelocityConfig;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.*;
import java.util.UUID;

public class VelocityPluginMessageListener {

    private final ReportSystemVelocity plugin;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public VelocityPluginMessageListener(ReportSystemVelocity plugin) {
        this.plugin = plugin;
    }

    private VelocityConfig config() {
        return plugin.getConfig();
    }

    private Component colorize(String text) {
        return LEGACY.deserialize(text);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(ReportSystemVelocity.CHANNEL)) {
            return;
        }

        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }

        // Mesajin client'a iletilmesini engelle
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
        try {
            String subchannel = in.readUTF();
            String serverName = connection.getServerInfo().getName();

            plugin.getLogger().info("[PLUGIN-MESSAGE] Received from {}: {}", serverName, subchannel);

            switch (subchannel) {
                case "CreateReport" -> handleCreateReport(connection, in, serverName);
                case "GlobalPunishment" -> handleGlobalPunishment(connection, in);
                case "GlobalUnpunishment" -> handleGlobalUnpunishment(connection, in);
                case "CheckPunishment" -> handleCheckPunishment(connection, in);
                case "CheckServerAndReplay" -> handleCheckServerAndReplay(in);
                default -> plugin.getLogger().warn("[PLUGIN-MESSAGE] Bilinmeyen subchannel: {} (sunucu: {})", subchannel, serverName);
            }
        } catch (IOException e) {
            plugin.getLogger().error("Plugin mesaji okunurken hata!", e);
        }
    }

    private void handleCreateReport(ServerConnection connection, DataInputStream in, String serverName) throws IOException {
        String reporterName = in.readUTF();
        String reporterUUID = in.readUTF();
        String targetName = in.readUTF();
        String reason = in.readUTF();

        plugin.getLogger().info("[REPORT] Creating report from {}: {} -> {}", serverName, reporterName, targetName);

        // Target oyuncunun UUID'sini bul
        String targetUUID;
        Player targetPlayer = plugin.getServer().getPlayer(targetName).orElse(null);
        if (targetPlayer != null) {
            targetUUID = targetPlayer.getUniqueId().toString();
        } else {
            targetUUID = UUID.randomUUID().toString();
            plugin.getLogger().warn("[REPORT] Target player {} is offline, using dummy UUID", targetName);
        }

        int maxReportsPerPlayer = plugin.getConfig().getInt("reports.max-reports-per-player", 3);

        // Report olustur (async)
        plugin.getReportService().createReport(
                reporterName, reporterUUID, targetName, targetUUID,
                reason, serverName, maxReportsPerPlayer
        ).thenAccept(reportId -> {
            if (reportId > 0) {
                plugin.getLogger().info("[REPORT] Report created with ID #{} on server: {}", reportId, serverName);

                plugin.getServer().getScheduler().buildTask(plugin, () -> {
                    // Bildirimleri gonder
                    sendReportNotifications(reportId, reporterName, targetName, reason, serverName);

                    // Rapor olusturan oyuncuya onay mesaji
                    plugin.getServer().getPlayer(reporterName).ifPresent(reporter ->
                            reporter.sendMessage(colorize(config().getMessage("report.created",
                                    "&a\u2713 Rapor basariyla olusturuldu! (ID: #%id%)",
                                    "%id%", String.valueOf(reportId))))
                    );

                    // Target oyuncunun sunucusuna report ID'yi gonder
                    Player target = plugin.getServer().getPlayer(targetName).orElse(null);
                    if (target != null) {
                        target.getCurrentServer().ifPresent(server -> {
                            byte[] response = createPluginMessage("ReportCreated", out -> {
                                out.writeUTF(reporterName);
                                out.writeInt(reportId);
                                out.writeUTF(targetName);
                            });
                            server.getServer().sendPluginMessage(ReportSystemVelocity.CHANNEL, response);
                            plugin.getLogger().info("[REPORT] Sent report ID #{} to server: {}",
                                    reportId, server.getServerInfo().getName());
                        });
                    }
                }).schedule();
            } else if (reportId == -2) {
                plugin.getLogger().warn("[REPORT] Report limit reached: {} -> {}", reporterName, targetName);
                plugin.getServer().getPlayer(reporterName).ifPresent(reporter ->
                        reporter.sendMessage(colorize(config().getMessage("report.limit-reached",
                                "&c\u2717 %player% adli oyuncuyu daha fazla rapor edemezsiniz! (Limit: %limit%)",
                                "%player%", targetName, "%limit%", String.valueOf(maxReportsPerPlayer))))
                );
            } else {
                plugin.getLogger().error("[REPORT] Invalid report ID returned: {}", reportId);
                plugin.getServer().getPlayer(reporterName).ifPresent(reporter ->
                        reporter.sendMessage(colorize(config().getMessage("report.error",
                                "&cRapor olusturulurken bir hata olustu!")))
                );
            }
        }).exceptionally(ex -> {
            plugin.getLogger().error("[REPORT] Report creation failed: {}", ex.getMessage(), ex);
            plugin.getServer().getPlayer(reporterName).ifPresent(reporter ->
                    reporter.sendMessage(colorize(config().getMessage("report.error",
                            "&cRapor olusturulurken bir hata olustu!")))
            );
            return null;
        });
    }

    private void handleCheckServerAndReplay(DataInputStream in) throws IOException {
        String playerName = in.readUTF();
        String reportServerName = in.readUTF();
        int reportId = in.readInt();

        Player player = plugin.getServer().getPlayer(playerName).orElse(null);
        if (player == null) {
            plugin.getLogger().warn("[REPLAY] Player not found: {}", playerName);
            return;
        }

        String currentServer = player.getCurrentServer()
                .map(conn -> conn.getServerInfo().getName())
                .orElse("");

        plugin.getLogger().info("[REPLAY] Check server - Player: {}, Current: {}, Report Server: {}",
                playerName, currentServer, reportServerName);

        if (currentServer.equalsIgnoreCase(reportServerName)) {
            // Ayni sunucuda, direkt replay baslat
            player.getCurrentServer().ifPresent(conn -> {
                byte[] msg = createPluginMessage("StartReplayDirect", out -> {
                    out.writeUTF(playerName);
                    out.writeInt(reportId);
                });
                conn.getServer().sendPluginMessage(ReportSystemVelocity.CHANNEL, msg);
            });
            plugin.getLogger().info("[REPLAY] Same server, starting replay directly on: {}", currentServer);
        } else {
            // Farkli sunucu, transfer gerekli
            RegisteredServer targetServer = plugin.getServer().getServer(reportServerName).orElse(null);
            if (targetServer == null) {
                plugin.getLogger().error("[REPLAY] Target server not found: {}", reportServerName);
                player.sendMessage(colorize(config().getMessage("report.server-not-found",
                        "&cHedef sunucu bulunamadi: %server%",
                        "%server%", reportServerName)));
                return;
            }

            player.getCurrentServer().ifPresent(conn -> {
                byte[] msg = createPluginMessage("TransferForReplay", out -> {
                    out.writeUTF(playerName);
                    out.writeUTF(reportServerName);
                    out.writeInt(reportId);
                });
                conn.getServer().sendPluginMessage(ReportSystemVelocity.CHANNEL, msg);
            });
            plugin.getLogger().info("[REPLAY] Sent transfer request to current server");
        }
    }

    private void sendReportNotifications(int reportId, String reporterName, String targetName,
                                         String reason, String serverName) {
        ReportNotificationListener notificationListener = plugin.getReportNotificationListener();
        if (notificationListener != null) {
            notificationListener.broadcastReportNotification(reportId, reporterName, targetName, reason, serverName);
        } else {
            plugin.getLogger().warn("[NOTIFICATION] ReportNotificationListener is null!");
        }
    }

    private void handleGlobalPunishment(ServerConnection connection, DataInputStream in) throws IOException {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();
        String reason = in.readUTF();
        String punisher = in.readUTF();
        long duration = in.readLong();
        String serverOrigin = connection.getServerInfo().getName();

        plugin.getLogger().info("[PUNISHMENT] {} request for {} from {}", type, playerName, serverOrigin);

        boolean success = switch (type) {
            case "ban" -> plugin.getPunishmentManager().globalBan(playerName, reason, punisher, duration, serverOrigin);
            case "mute" -> plugin.getPunishmentManager().globalMute(playerName, reason, punisher, duration, serverOrigin);
            case "kick" -> plugin.getPunishmentManager().globalKick(playerName, reason, punisher, serverOrigin);
            default -> false;
        };

        byte[] response = createPluginMessage("PunishmentResult", out -> {
            out.writeUTF(requestId);
            out.writeBoolean(success);
        });
        connection.getServer().sendPluginMessage(ReportSystemVelocity.CHANNEL, response);
    }

    private void handleGlobalUnpunishment(ServerConnection connection, DataInputStream in) throws IOException {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();

        boolean success = switch (type) {
            case "unban" -> plugin.getPunishmentManager().removePunishment(playerName, "ban");
            case "unmute" -> plugin.getPunishmentManager().removePunishment(playerName, "mute");
            default -> false;
        };

        byte[] response = createPluginMessage("UnpunishmentResult", out -> {
            out.writeUTF(requestId);
            out.writeBoolean(success);
        });
        connection.getServer().sendPluginMessage(ReportSystemVelocity.CHANNEL, response);
    }

    private void handleCheckPunishment(ServerConnection connection, DataInputStream in) throws IOException {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();

        boolean result = plugin.getPunishmentManager().isActivePunishment(playerName, type);

        byte[] response = createPluginMessage("CheckPunishmentResult", out -> {
            out.writeUTF(requestId);
            out.writeBoolean(result);
        });
        connection.getServer().sendPluginMessage(ReportSystemVelocity.CHANNEL, response);
    }

    private byte[] createPluginMessage(String subchannel, MessageWriter writer) {
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(msgBytes);
        try {
            out.writeUTF(subchannel);
            writer.write(out);
        } catch (IOException e) {
            plugin.getLogger().error("Plugin mesaji olusturulurken hata!", e);
        }
        return msgBytes.toByteArray();
    }

    @FunctionalInterface
    private interface MessageWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
