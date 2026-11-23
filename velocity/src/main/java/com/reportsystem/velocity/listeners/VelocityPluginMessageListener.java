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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityPluginMessageListener {

    private static final long SIG_WARN_INTERVAL_MS = 60_000L;

    private final ReportSystemVelocity plugin;
    /** Guvenlik uyarisi hiz siniri: anahtar (kaynak) -> son uyari zamani. */
    private final Map<String, Long> lastSigWarn = new ConcurrentHashMap<>();

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
        boolean main = event.getIdentifier().equals(ReportSystemVelocity.CHANNEL);
        if (!main && !event.getIdentifier().equals(ReportSystemVelocity.NOTIFICATION_CHANNEL)) {
            return;
        }

        // Kanal yalnizca proxy <-> backend arasindadir: ne istemciye ne de istemciden iletilir.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        // Istemciden (oyuncu baglantisindan) gelen her sey atilir (sahte mesaj denemesi)
        if (!(event.getSource() instanceof ServerConnection connection)) {
            String sender = event.getSource() instanceof Player p ? p.getUsername() : String.valueOf(event.getSource());
            if (shouldWarn("client:" + sender)) {
                plugin.getLogger().warn("[SECURITY] Istemci kaynakli plugin message atildi ({}): {} (60 sn icinde tekrarlar loglanmaz)",
                        event.getIdentifier().getId(), event.getSource());
            }
            return;
        }

        // Bildirim kanali: backend'in yerel olarak olusturdugu raporu (NewReport) diger sunuculara yayar
        if (!main) {
            handleBackendNotification(connection, event.getIdentifier().getId(), event.getData());
            return;
        }

        // HMAC-SHA256 dogrulamasi (bkz. MessageSigner)
        byte[] payload = plugin.getMessageSigner().verify(event.getIdentifier().getId(), event.getData());
        if (payload == null) {
            String server = connection.getServerInfo().getName();
            if (shouldWarn("main:" + server)) {
                plugin.getLogger().warn("[SECURITY] Gecersiz imza/zaman damgasi, mesaj reddedildi (sunucu: {}). "
                        + "message-secret tum sunucularda ayni mi? (60 sn icinde tekrarlar loglanmaz)", server);
            }
            return;
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        try {
            String subchannel = in.readUTF();
            String serverName = connection.getServerInfo().getName();

            // Her mesajda kosulsuz INFO log taskini yapmasin: yalniz debug.log-messages acikken
            if (config().getBoolean("debug.log-messages", false)) {
                plugin.getLogger().info("[PLUGIN-MESSAGE] Received from {}: {}", serverName, subchannel);
            }

            switch (subchannel) {
                case "CreateReport" -> handleCreateReport(connection, in, serverName);
                case "GlobalPunishment" -> handleGlobalPunishment(connection, in);
                case "GlobalUnpunishment" -> handleGlobalUnpunishment(connection, in);
                case "CheckPunishment" -> handleCheckPunishment(connection, in);
                case "CheckServerAndReplay" -> handleCheckServerAndReplay(connection, in);
                default -> plugin.getLogger().warn("[PLUGIN-MESSAGE] Bilinmeyen subchannel: {} (sunucu: {})", subchannel, serverName);
            }
        } catch (IOException e) {
            plugin.getLogger().error("Plugin mesaji okunurken hata!", e);
        }
    }

    /** reportsystem:notification (backend -> proxy): NewReport(int id, UTF reporter, UTF reported, UTF reason, UTF server). */
    private void handleBackendNotification(ServerConnection connection, String channelId, byte[] data) {
        byte[] payload = plugin.getMessageSigner().verify(channelId, data);
        if (payload == null) {
            String server = connection.getServerInfo().getName();
            if (shouldWarn("notification:" + server)) {
                plugin.getLogger().warn("[SECURITY] Gecersiz imzali bildirim reddedildi (sunucu: {}). "
                        + "(60 sn icinde tekrarlar loglanmaz)", server);
            }
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            if (!in.readUTF().equals("NewReport")) {
                return;
            }
            int reportId = in.readInt();
            String reporter = in.readUTF();
            String reported = in.readUTF();
            String reason = in.readUTF();
            String serverName = in.readUTF();
            ReportNotificationListener listener = plugin.getReportNotificationListener();
            if (listener != null) {
                // Kaynak sunucu kendi yetkililerine zaten yerel bildirim gonderdi -> tekrar gonderme
                listener.broadcastReportNotification(reportId, reporter, reported, reason, serverName,
                        connection.getServerInfo().getName());
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warn("[NOTIFICATION] Bozuk bildirim mesaji: {}", e.getMessage());
        }
    }

    /**
     * Oyuncunun SU ANKI sunucusuna imzali StartReplay gonderir:
     * (oyuncu UUID UTF, reportId int, kaynak sunucu UTF — bossa donus yok).
     * Kaynak alani sona eklendi: eski Spigot fazla bayti okumaz (geriye uyumlu).
     */
    private void sendStartReplay(Player player, int reportId, String origin) {
        player.getCurrentServer().ifPresent(conn -> {
            byte[] msg = createPluginMessage("StartReplay", out -> {
                out.writeUTF(player.getUniqueId().toString());
                out.writeInt(reportId);
                out.writeUTF(origin != null ? origin : "");
            });
            plugin.sendSigned(conn.getServer(), ReportSystemVelocity.CHANNEL, msg);
        });
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
                                out.writeUTF(target.getUniqueId().toString());
                                out.writeInt(reportId);
                            });
                            plugin.sendSigned(server.getServer(), ReportSystemVelocity.CHANNEL, response);
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

    private void handleCheckServerAndReplay(ServerConnection connection, DataInputStream in) throws IOException {
        String playerName = in.readUTF();
        String reportServerName = in.readUTF();
        int reportId = in.readInt();

        Player player = plugin.getServer().getPlayer(playerName).orElse(null);
        ServerConnection current = player != null ? player.getCurrentServer().orElse(null) : null;
        if (player == null || current == null) {
            plugin.getLogger().warn("[REPLAY] Player not found or not connected: {}", playerName);
            return;
        }

        // H-21: istek, oyuncunun BULUNDUGU sunucudan gelmeli (baska sunucu adina oyuncu tasinamasin)
        String senderServer = connection.getServerInfo().getName();
        if (!current.getServerInfo().getName().equals(senderServer)) {
            if (shouldWarn("replay:" + senderServer)) {
                plugin.getLogger().warn("[SECURITY] CheckServerAndReplay reddedildi: {} su an {} sunucusunda, istek {} "
                                + "sunucusundan geldi. (60 sn icinde tekrarlar loglanmaz)",
                        playerName, current.getServerInfo().getName(), senderServer);
            }
            return;
        }

        String currentServer = current.getServerInfo().getName();

        plugin.getLogger().info("[REPLAY] Check server - Player: {}, Current: {}, Report Server: {}",
                playerName, currentServer, reportServerName);

        if (currentServer.equalsIgnoreCase(reportServerName)) {
            // Ayni sunucuda, direkt replay baslat (donus sunucusu yok)
            sendStartReplay(player, reportId, "");
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

            // Oyuncuyu proxy bagla; baglaninca hedef sunucuya StartReplay gider.
            // H-20: hedef sunucu replay bitince izleyiciyi buraya (kaynak) geri gonderir.
            final String origin = currentServer;
            player.createConnectionRequest(targetServer).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    sendStartReplay(player, reportId, origin);
                } else {
                    plugin.getLogger().warn("[REPLAY] Could not connect {} to {}", playerName, reportServerName);
                }
            }).exceptionally(ex -> {
                // K-2: baglanti istegi (veya StartReplay gonderimi) istisnayla biterse sessizce yutulmasin
                plugin.getLogger().warn("[REPLAY] Connection request failed for {} to {}", playerName, reportServerName, ex);
                return null;
            });
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

        // Alanlar olay thread'inde okundu; ceza isi (DB, saglayici) async yapilir
        RegisteredServer origin = connection.getServer();
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            boolean success = false;
            try {
                success = switch (type) {
                    case "ban" -> plugin.getPunishmentManager().globalBan(playerName, reason, punisher, duration, serverOrigin);
                    case "mute" -> plugin.getPunishmentManager().globalMute(playerName, reason, punisher, duration, serverOrigin);
                    case "kick" -> plugin.getPunishmentManager().globalKick(playerName, reason, punisher, serverOrigin);
                    case "warn" -> plugin.getPunishmentManager().globalWarn(playerName, reason, punisher, serverOrigin);
                    default -> false;
                };
            } catch (RuntimeException e) {
                plugin.getLogger().error("[PUNISHMENT] {} islenemedi: {}", type, playerName, e);
            }

            final boolean result = success;
            byte[] response = createPluginMessage("PunishmentResult", out -> {
                out.writeUTF(requestId);
                out.writeBoolean(result);
            });
            plugin.sendSigned(origin, ReportSystemVelocity.CHANNEL, response);
        }).schedule();
    }

    private void handleGlobalUnpunishment(ServerConnection connection, DataInputStream in) throws IOException {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();

        RegisteredServer origin = connection.getServer();
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            boolean success = false;
            try {
                success = switch (type) {
                    case "unban" -> plugin.getPunishmentManager().removePunishment(playerName, "ban");
                    case "unmute" -> plugin.getPunishmentManager().removePunishment(playerName, "mute");
                    default -> false;
                };
            } catch (RuntimeException e) {
                plugin.getLogger().error("[PUNISHMENT] {} islenemedi: {}", type, playerName, e);
            }

            final boolean result = success;
            byte[] response = createPluginMessage("UnpunishmentResult", out -> {
                out.writeUTF(requestId);
                out.writeBoolean(result);
            });
            plugin.sendSigned(origin, ReportSystemVelocity.CHANNEL, response);
        }).schedule();
    }

    private void handleCheckPunishment(ServerConnection connection, DataInputStream in) throws IOException {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();

        RegisteredServer origin = connection.getServer();
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            boolean active = false;
            try {
                active = plugin.getPunishmentManager().isActivePunishment(playerName, type);
            } catch (RuntimeException e) {
                plugin.getLogger().error("[PUNISHMENT] Ceza kontrolu basarisiz: {}", playerName, e);
            }

            final boolean result = active;
            byte[] response = createPluginMessage("CheckPunishmentResult", out -> {
                out.writeUTF(requestId);
                out.writeBoolean(result);
            });
            plugin.sendSigned(origin, ReportSystemVelocity.CHANNEL, response);
        }).schedule();
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

    /**
     * Imza/kaynak uyarilarini anahtar basina 60 sn'de bire sinirlar (H-19: log taskini).
     */
    private boolean shouldWarn(String key) {
        long now = System.currentTimeMillis();
        boolean[] warn = {false};
        lastSigWarn.compute(key, (k, prev) -> {
            if (prev == null || now - prev >= SIG_WARN_INTERVAL_MS) {
                warn[0] = true;
                return now;
            }
            return prev;
        });
        if (warn[0] && lastSigWarn.size() > 256) {
            lastSigWarn.values().removeIf(t -> now - t >= SIG_WARN_INTERVAL_MS);
        }
        return warn[0];
    }

    @FunctionalInterface
    private interface MessageWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
