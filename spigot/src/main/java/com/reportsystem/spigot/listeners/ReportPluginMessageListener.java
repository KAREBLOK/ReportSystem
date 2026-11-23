package com.reportsystem.spigot.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.punishment.providers.BungeeCordPunishmentProvider;
import com.reportsystem.spigot.punishment.providers.GlobalPunishmentProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReportPluginMessageListener implements PluginMessageListener {

    private static final long SIG_WARN_INTERVAL_MS = 60_000L;

    private final ReportSystemSpigot plugin;
    /** Imza uyarisi hiz siniri: anahtar (tasiyici oyuncu) -> son uyari zamani. */
    private final Map<String, Long> lastSigWarn = new ConcurrentHashMap<>();

    public ReportPluginMessageListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(plugin.getChannelName())) {
            return;
        }

        // HMAC-SHA256 dogrulamasi (bkz. MessageSigner): imzasiz/sahte/eski mesaj atilir.
        // Istemci de bu kanala ham payload gonderebildigi icin kaynak yalnizca imzayla dogrulanir.
        if (plugin.getMessageSigner() == null) {
            return;
        }
        byte[] payload = plugin.getMessageSigner().verify(channel, message);
        if (payload == null) {
            String carrier = player != null ? player.getUniqueId().toString() : "?";
            if (shouldWarn(channel + ":" + carrier)) {
                plugin.getLogger().warning("[PLUGIN-MESSAGE] Gecersiz imza/zaman damgasi, mesaj reddedildi (tasiyici: "
                        + (player != null ? player.getName() : "?") + "). message-secret tum sunucularda ayni mi?"
                        + " (60 sn icinde tekrarlar loglanmaz)");
            }
            return;
        }

        ByteArrayDataInput in;
        String subChannel;
        try {
            in = ByteStreams.newDataInput(payload);
            subChannel = in.readUTF();
        } catch (RuntimeException e) {
            return;
        }
        try {
            dispatch(subChannel, in);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[PLUGIN-MESSAGE] Bozuk mesaj (" + subChannel + "): " + e.getMessage());
        }
    }

    private void dispatch(String subChannel, ByteArrayDataInput in) {

        switch (subChannel) {
            case "ReportCreated":
                handleReportCreated(in);
                break;

            case "StartReplay":
                handleStartReplay(in);
                break;

            // Proxy (Velocity/Bungee) bu 3 subchannel ile cevap gönderir:
            // PunishmentResult → ban/mute/kick/warn sonucu
            // UnpunishmentResult → unban/unmute sonucu
            // PunishmentResponse → legacy isim (eski akış için korundu)
            case "PunishmentResponse":
            case "PunishmentResult":
            case "UnpunishmentResult":
                handlePunishmentResponse(in);
                break;

            case "CheckPunishmentResult":
                handleCheckPunishmentResponse(in);
                break;

            default:
                plugin.getLogger().warning("Unknown subchannel: " + subChannel);
        }
    }

    private void handleReportCreated(ByteArrayDataInput in) {
        // Alan sirasi: hedef (kaydedilen) oyuncunun UUID'si (UTF), rapor ID (int).
        // Kayit, raporlanan oyuncu uzerinde tutulur; proxy mesaji onun sunucusuna yollar.
        String targetUUID = in.readUTF();
        int reportId = in.readInt();

        java.util.UUID uuid = parseUUID(targetUUID);
        if (uuid == null) return;

        // Recording için report ID'yi güncelle
        plugin.updateRecordingReportId(uuid, reportId);

        plugin.debug("[DEBUG] Report created response received: #" + reportId);
    }

    private void handleStartReplay(ByteArrayDataInput in) {
        // Alan sirasi: izleyici UUID (UTF), rapor ID (int), [kaynak sunucu (UTF) — yeni proxy]
        String playerUUID = in.readUTF();
        int reportId = in.readInt();
        // H-20: eski proxy bu alani yazmaz -> bos kabul edilir (geriye uyumlu)
        String origin = "";
        try {
            origin = in.readUTF();
        } catch (RuntimeException ignored) {
            // Eski proxy surumu: kaynak sunucu alani yok
        }

        java.util.UUID uuid = parseUUID(playerUUID);
        if (uuid == null) return;

        com.reportsystem.spigot.replay.ReplayManager replayManager = plugin.getReplayManager();
        if (replayManager == null) return;

        // H-20: replay bitince izleyicinin geri gonderilecegi sunucu (oyuncu henuz bu sunucuda
        // olmasa da; bekleyen replay katildiginda baslar ve bitince bu kayitla geri doner)
        boolean hasOrigin = !origin.isEmpty() && !origin.equalsIgnoreCase(plugin.getServerName());

        // Replay başlatma isteği
        Player targetPlayer = plugin.getServer().getPlayer(uuid);
        if (targetPlayer == null) {
            // Oyuncu bu sunucuda değil, bekleyen replay olarak kaydet (30 sn dolarsa donus kaydi da silinir)
            if (hasOrigin) {
                replayManager.rememberOriginServer(uuid, origin);
            }
            plugin.storePendingReplay(uuid, reportId);
            return;
        }

        // Savunma derinligi: imza gecerli olsa bile izleyici yetkili degilse replay baslatma
        if (!targetPlayer.hasPermission("reportsystem.view.all")) {
            plugin.getLogger().warning("[PLUGIN-MESSAGE] StartReplay reddedildi: " + targetPlayer.getName()
                    + " reportsystem.view.all yetkisine sahip degil.");
            return;
        }

        // M-6: Overwatch incelemesindeki (post-replay dahil) oyuncu normal replay baslatamaz:
        // iki oturumun envanter/konum kayitlari cakisir (dupe yolu). GUIListener.watchReplay ile ayni kontrol.
        com.reportsystem.spigot.overwatch.listeners.OverwatchReplayListener owl = plugin.getOverwatchReplayListener();
        if (owl != null && owl.isReviewing(uuid)) {
            plugin.getMessageManager().sendMessage(targetPlayer, "replay.already-watching");
            return;
        }

        // Halihazirda izledigi replay'in donus kaydi ezilmesin (startReplay zaten "already-watching" ile reddeder)
        if (hasOrigin && !replayManager.isWatchingReplay(uuid)) {
            replayManager.rememberOriginServer(uuid, origin);
        }
        replayManager.startReplay(reportId, targetPlayer).thenAccept(started -> {
            // K-3: replay baslamadiysa donus kaydi kalmasin (yoksa sonraki yerel replay bitince
            // izleyici yanlislikla kaynak sunucuya gonderilir). Aktif replay'in kaydina dokunma.
            if (hasOrigin && !Boolean.TRUE.equals(started) && !replayManager.isWatchingReplay(uuid)) {
                replayManager.forgetOriginServer(uuid);
            }
        });
    }

    private void handlePunishmentResponse(ByteArrayDataInput in) {
        String requestId = in.readUTF();
        boolean result = in.readBoolean();

        // Hangi provider aktifse ona yönlendir
        Object provider = plugin.getPunishmentManager().getProvider();
        if (provider instanceof GlobalPunishmentProvider) {
            ((GlobalPunishmentProvider) provider).handleResponse(requestId, result);
        } else if (provider instanceof BungeeCordPunishmentProvider) {
            ((BungeeCordPunishmentProvider) provider).handlePunishmentResult(requestId, result);
        }
    }

    private void handleCheckPunishmentResponse(ByteArrayDataInput in) {
        String requestId = in.readUTF();
        boolean result = in.readBoolean();

        Object provider = plugin.getPunishmentManager().getProvider();
        if (provider instanceof GlobalPunishmentProvider) {
            // Global provider check request için de aynı pendingRequests map'ini kullanıyor
            ((GlobalPunishmentProvider) provider).handleResponse(requestId, result);
        } else if (provider instanceof BungeeCordPunishmentProvider) {
            ((BungeeCordPunishmentProvider) provider).handleCheckResult(requestId, result);
        }
    }

    private java.util.UUID parseUUID(String raw) {
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[PLUGIN-MESSAGE] Geçersiz UUID formatı: " + raw);
            return null;
        }
    }

    /**
     * Imza uyarilarini anahtar basina 60 sn'de bire sinirlar (log taskini; bkz. proxy dinleyicileri).
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