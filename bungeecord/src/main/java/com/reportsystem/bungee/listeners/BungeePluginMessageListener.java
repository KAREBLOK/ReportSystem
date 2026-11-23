package com.reportsystem.bungee.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.bungee.ReportSystemBungee;
import com.reportsystem.common.models.Report;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class BungeePluginMessageListener implements Listener {

    private static final long SIG_WARN_INTERVAL_MS = 60_000L;

    /** K-a/K-b: bekleyen StartReplay zaman asimi (sn, K-j: 120). */
    private static final long PENDING_REPLAY_TIMEOUT_SEC = 120L;

    /** Staff'i hedef sunucuya baglarken bekleyen StartReplay istegi. */
    private record PendingReplay(ServerInfo target, int reportId, String origin, long createdAt) { }

    private final ReportSystemBungee plugin;
    /** Bekleyen replay'ler: staff UUID -> istek (ServerConnectedEvent'te gonderilip silinir). */
    private final Map<UUID, PendingReplay> pendingReplays = new ConcurrentHashMap<>();
    /** Güvenlik uyarısı hız sınırı: anahtar (kaynak) → son uyarı zamanı. */
    private final Map<String, Long> lastSigWarn = new ConcurrentHashMap<>();

    public BungeePluginMessageListener(ReportSystemBungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        String tag = event.getTag();
        boolean main = tag.equals("reportsystem:channel");
        if (!main && !tag.equals("reportsystem:notification")) {
            return;
        }

        // Kanal yalnizca proxy <-> backend arasindadir: ne istemciye ne de istemciden iletilir.
        event.setCancelled(true);

        // Istemciden (oyuncu baglantisindan) gelen her sey atilir (sahte mesaj denemesi)
        if (!(event.getSender() instanceof Server)) {
            String sender = event.getSender() instanceof ProxiedPlayer p ? p.getName() : String.valueOf(event.getSender());
            if (shouldWarn("client:" + sender)) {
                plugin.getLogger().warning("[SECURITY] Istemci kaynakli plugin message atildi (" + tag + "): " + event.getSender()
                        + " (60 sn icinde tekrarlar loglanmaz)");
            }
            return;
        }

        Server connection = (Server) event.getSender();

        // Bildirim kanali: backend'in yerel olarak olusturdugu raporu (NewReport) diger sunuculara yayar
        if (!main) {
            handleBackendNotification(connection, tag, event.getData());
            return;
        }

        // HMAC-SHA256 dogrulamasi (bkz. MessageSigner)
        byte[] payload = plugin.getMessageSigner().verify(tag, event.getData());
        if (payload == null) {
            String server = connection.getInfo().getName();
            if (shouldWarn("main:" + server)) {
                plugin.getLogger().warning("[SECURITY] Gecersiz imza/zaman damgasi, mesaj reddedildi (sunucu: "
                        + server + "). message-secret tum sunucularda ayni mi? (60 sn icinde tekrarlar loglanmaz)");
            }
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(payload);

        String subchannel;
        try {
            subchannel = in.readUTF();
        } catch (RuntimeException e) {
            return;
        }

        // Otomatik olarak hangi sunucudan geldiğini al
        String serverName = connection.getInfo().getName();

        // Her mesajda koşulsuz INFO log taşkını yapmasın: yalnız debug.log-messages açıkken
        if (plugin.getConfig().getBoolean("debug.log-messages", false)) {
            plugin.getLogger().info("[PLUGIN-MESSAGE] Received from " + serverName + ": " + subchannel);
        }

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

    /** reportsystem:notification (backend -> proxy): NewReport(int id, UTF reporter, UTF reported, UTF reason, UTF server). */
    private void handleBackendNotification(Server connection, String tag, byte[] data) {
        byte[] payload = plugin.getMessageSigner().verify(tag, data);
        if (payload == null) {
            String server = connection.getInfo().getName();
            if (shouldWarn("notification:" + server)) {
                plugin.getLogger().warning("[SECURITY] Gecersiz imzali bildirim reddedildi (sunucu: "
                        + server + "). (60 sn icinde tekrarlar loglanmaz)");
            }
            return;
        }
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(payload);
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
                        connection.getInfo().getName());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[NOTIFICATION] Bozuk bildirim mesaji: " + e.getMessage());
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
                    // K-1 deseni: getServer() bir kez okunur (sunucu degisimi sirasinda null/farkli donebilir)
                    Server targetServer = target != null ? target.getServer() : null;
                    if (targetServer != null) {
                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeUTF("ReportCreated");
                        out.writeUTF(target.getUniqueId().toString());
                        out.writeInt(reportId);

                        plugin.sendSigned(targetServer, "reportsystem:channel", out.toByteArray());

                        plugin.getLogger().info("[REPORT] Sent report ID #" + reportId +
                                " to server: " + targetServer.getInfo().getName());
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
        // K-1: getServer() bir kez okunur (sunucu degisimi sirasinda cagrilar arasi null/farkli donebilir)
        Server playerServer = player != null ? player.getServer() : null;
        if (playerServer == null) {
            plugin.getLogger().warning("[REPLAY] Player not found or not connected: " + playerName);
            return;
        }
        String currentServer = playerServer.getInfo().getName();

        // H-21: istek, oyuncunun BULUNDUGU sunucudan gelmeli (baska sunucu adina oyuncu tasinamasin)
        String senderServer = connection.getInfo().getName();
        if (!currentServer.equals(senderServer)) {
            if (shouldWarn("replay:" + senderServer)) {
                plugin.getLogger().warning("[SECURITY] CheckServerAndReplay reddedildi: " + playerName
                        + " su an " + currentServer + " sunucusunda, istek "
                        + senderServer + " sunucusundan geldi. (60 sn icinde tekrarlar loglanmaz)");
            }
            return;
        }

        plugin.getLogger().info("[REPLAY] Check server - Player: " + playerName +
                ", Current: " + currentServer + ", Report Server: " + reportServerName);

        // Aynı sunucuda mı kontrol et
        if (currentServer.equalsIgnoreCase(reportServerName)) {
            // Aynı sunucuda, direkt replay başlat (dönüş sunucusu yok)
            // (oyuncunun kendi baglantisi uzerinden; onceki davranis korunur)
            plugin.sendSigned(playerServer, "reportsystem:channel", buildStartReplay(player, reportId, ""));
            plugin.getLogger().info("[REPLAY] Same server, starting replay directly on: " + currentServer);
        } else {
            // Farklı sunucu: oyuncuyu proxy bağlar, bağlanınca hedef sunucuya StartReplay gider
            plugin.getLogger().info("[REPLAY] Different server, need to transfer from " +
                    currentServer + " to " + reportServerName);

            net.md_5.bungee.api.config.ServerInfo targetInfo = ProxyServer.getInstance().getServerInfo(reportServerName);
            if (targetInfo == null) {
                plugin.getLogger().severe("[REPLAY] Target server not found: " + reportServerName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.report.server-not-found",
                                "&cHedef sunucu bulunamadı: %server%"))
                        .replace("%server%", reportServerName));
                return;
            }

            // H-20: hedef sunucu replay bitince izleyiciyi buraya geri gönderir
            final String origin = currentServer;
            // E-1: Bungee connect callback'i backend login/setServer ONCESI (TCP baglantisinda) cagrilir;
            // o an player.getServer() hala KAYNAK sunucudur. StartReplay bu yuzden dogrudan hedef
            // ServerInfo'ya gider: ServerInfo.sendData hedefte oyuncu varsa hemen iletir (Spigot
            // storePendingReplay ile 30 sn bekletir, izleyici katilinca baslar), yoksa kuyruklar ve
            // izleyicinin baglantisi kurulunca iletir.
            // K-a/K-b: paket artik connect callback'inde degil, ServerConnectedEvent'te (staff hedefe
            // GERCEKTEN baglaninca) event.getServer() uzerinden gider; bekleyen harita zaman asimi,
            // baglanti hatasi ve oyuncu cikisinda temizlenir (paket baska oyuncunun baglantisina sizmaz).
            final UUID staffId = player.getUniqueId();
            final PendingReplay pending = new PendingReplay(targetInfo, reportId, origin, System.currentTimeMillis());
            pendingReplays.put(staffId, pending);
            plugin.getProxy().getScheduler().schedule(plugin, () -> {
                if (pendingReplays.remove(staffId, pending)) {
                    plugin.getLogger().warning("[REPLAY] Pending replay timed out for " + playerName
                            + " (" + reportServerName + ")");
                    if (player.isConnected()) {
                        sendReplayFailed(player, reportServerName);
                    }
                }
            }, PENDING_REPLAY_TIMEOUT_SEC, TimeUnit.SECONDS);

            ServerConnectRequest request = ServerConnectRequest.builder()
                    .target(targetInfo)
                    .reason(ServerConnectEvent.Reason.PLUGIN)
                    .callback((net.md_5.bungee.api.Callback<ServerConnectRequest.Result>) (result, error) -> {
                        if (error == null && result != null) {
                            switch (result) {
                                case SUCCESS:
                                case ALREADY_CONNECTING:
                                    return; // gonderim ServerConnectedEvent'te (K-i)
                                case ALREADY_CONNECTED:
                                    if (pendingReplays.remove(staffId, pending)) {
                                        Server now = player.getServer();
                                        if (now != null && now.getInfo().getName().equals(targetInfo.getName())) {
                                            plugin.sendSigned(now, "reportsystem:channel",
                                                    buildStartReplay(player, reportId, origin));
                                        } else {
                                            sendReplayFailed(player, reportServerName);
                                        }
                                    }
                                    return;
                                default:
                                    break; // FAIL / EVENT_CANCEL
                            }
                        }
                        if (!pendingReplays.remove(staffId, pending)) {
                            return; // zaten gonderildi / temizlendi
                        }
                        if (error != null) {
                            plugin.getLogger().log(Level.WARNING, "[REPLAY] Could not connect " + playerName
                                    + " to " + reportServerName, error);
                        } else {
                            plugin.getLogger().warning("[REPLAY] Could not connect " + playerName + " to "
                                    + reportServerName + " (" + result + ")");
                        }
                        sendReplayFailed(player, reportServerName);
                    })
                    .build();
            player.connect(request);
        }
    }

    private void sendReplayFailed(ProxiedPlayer player, String serverName) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.report.replay-connect-failed",
                        "&cReplay başlatılamadı: &f%server% &csunucusuna bağlanılamadı. &7(Could not connect to the target server.)")
                        .replace("%server%", serverName)));
    }

    /** K-k: hedefe baglanirken kick yenirse bekleyen replay iptal edilir. */
    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        if (event.getState() != ServerKickEvent.State.CONNECTING || event.getKickedFrom() == null) {
            return;
        }
        ProxiedPlayer player = event.getPlayer();
        PendingReplay pending = pendingReplays.get(player.getUniqueId());
        if (pending == null || !pending.target().getName().equals(event.getKickedFrom().getName())) {
            return;
        }
        if (pendingReplays.remove(player.getUniqueId(), pending)) {
            sendReplayFailed(player, pending.target().getName());
        }
    }

    /** K-a/K-b: staff hedef sunucuya baglandi -> bekleyen StartReplay'i o baglantidan gonder. */
    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        ProxiedPlayer player = event.getPlayer();
        PendingReplay pending = pendingReplays.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if (!pending.target().getName().equals(event.getServer().getInfo().getName())) {
            return; // baska sunucuya baglandi (orn. fallback); zaman asimina kadar bekle
        }
        if (!pendingReplays.remove(player.getUniqueId(), pending)) {
            return;
        }
        plugin.sendSigned(event.getServer(), "reportsystem:channel",
                buildStartReplay(player, pending.reportId(), pending.origin()));
        plugin.getLogger().info("[REPLAY] StartReplay sent to " + player.getName() + " on " + pending.target().getName());
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        pendingReplays.remove(event.getPlayer().getUniqueId());
    }

    /**
     * StartReplay yükü (imzalama sendSigned'da):
     * (oyuncu UUID UTF, reportId int, kaynak sunucu UTF — boşsa dönüş yok).
     * Kaynak alanı sona eklendi: eski Spigot fazla baytı okumaz (geriye uyumlu).
     */
    private byte[] buildStartReplay(ProxiedPlayer player, int reportId, String origin) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("StartReplay");
        out.writeUTF(player.getUniqueId().toString());
        out.writeInt(reportId);
        out.writeUTF(origin != null ? origin : "");
        return out.toByteArray();
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
        // Alanlar Netty thread'inde okunur; ceza işi (DB, sağlayıcı) async yapılır
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();
        String reason = in.readUTF();
        String punisher = in.readUTF();
        long duration = in.readLong();
        String serverOrigin = connection.getInfo().getName();

        plugin.getLogger().info("[PUNISHMENT] " + type + " request for " + playerName + " from " + serverOrigin);

        // Yanit SUNUCUYA (ServerInfo) gider: oyuncunun Server baglantisi bu arada kapanmis/degismis olabilir
        ServerInfo origin = connection.getInfo();
        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            boolean success = false;
            try {
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
                    case "warn":
                        success = plugin.getPunishmentManager().globalWarn(playerName, reason, punisher, serverOrigin);
                        break;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "[PUNISHMENT] " + type + " işlenemedi: " + playerName, e);
            }

            // Sonucu geri gönder
            ByteArrayDataOutput response = ByteStreams.newDataOutput();
            response.writeUTF("PunishmentResult");
            response.writeUTF(requestId);
            response.writeBoolean(success);

            plugin.sendSigned(origin, "reportsystem:channel", response.toByteArray());
        });
    }

    private void handleGlobalUnpunishment(Server connection, ByteArrayDataInput in) {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();

        // Yanit SUNUCUYA (ServerInfo) gider: oyuncunun Server baglantisi bu arada kapanmis/degismis olabilir
        ServerInfo origin = connection.getInfo();
        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            boolean success = false;
            try {
                switch (type) {
                    case "unban":
                        success = plugin.getPunishmentManager().removePunishment(playerName, "ban");
                        break;
                    case "unmute":
                        success = plugin.getPunishmentManager().removePunishment(playerName, "mute");
                        break;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "[PUNISHMENT] " + type + " işlenemedi: " + playerName, e);
            }

            ByteArrayDataOutput response = ByteStreams.newDataOutput();
            response.writeUTF("UnpunishmentResult");
            response.writeUTF(requestId);
            response.writeBoolean(success);

            plugin.sendSigned(origin, "reportsystem:channel", response.toByteArray());
        });
    }

    private void handleCheckPunishment(Server connection, ByteArrayDataInput in) {
        String requestId = in.readUTF();
        String playerName = in.readUTF();
        String type = in.readUTF();

        // Yanit SUNUCUYA (ServerInfo) gider: oyuncunun Server baglantisi bu arada kapanmis/degismis olabilir
        ServerInfo origin = connection.getInfo();
        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            boolean result = false;
            try {
                result = plugin.getPunishmentManager().isActivePunishment(playerName, type);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "[PUNISHMENT] Ceza kontrolü başarısız: " + playerName, e);
            }

            ByteArrayDataOutput response = ByteStreams.newDataOutput();
            response.writeUTF("CheckPunishmentResult");
            response.writeUTF(requestId);
            response.writeBoolean(result);

            plugin.sendSigned(origin, "reportsystem:channel", response.toByteArray());
        });
    }

    /**
     * Imza/kaynak uyarilarini anahtar basina 60 sn'de bire sinirlar (H-19: log taşkını).
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
}