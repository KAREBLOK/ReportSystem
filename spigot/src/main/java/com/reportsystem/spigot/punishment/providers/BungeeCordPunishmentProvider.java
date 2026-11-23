package com.reportsystem.spigot.punishment.providers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.common.punishment.PunishmentProvider;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BungeeCordPunishmentProvider implements PunishmentProvider {

    private final ReportSystemSpigot plugin;
    private final ProxyRequestTracker tracker;
    private final Map<String, Integer> playerWarnings = new HashMap<>();

    public BungeeCordPunishmentProvider(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.tracker = new ProxyRequestTracker(plugin);
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long durationMillis) {
        plugin.getLogger().info("[BUNGEECORD] Sending global ban request for " + playerName);
        return sendPunishmentRequest(playerName, "ban", reason, punisher, durationMillis);
    }

    @Override
    public boolean tempBan(String playerName, String reason, String punisher, long durationMillis) {
        return ban(playerName, reason, punisher, durationMillis);
    }

    @Override
    public boolean permBan(String playerName, String reason, String punisher) {
        return ban(playerName, reason, punisher, -1);
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long durationMillis) {
        plugin.getLogger().info("[BUNGEECORD] Sending global mute request for " + playerName);
        return sendPunishmentRequest(playerName, "mute", reason, punisher, durationMillis);
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        plugin.getLogger().info("[BUNGEECORD] Sending global kick request for " + playerName);
        return sendPunishmentRequest(playerName, "kick", reason, punisher, 0);
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        // Warn local olarak tutulur
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            int warnings = playerWarnings.getOrDefault(playerName, 0) + 1;
            com.reportsystem.spigot.managers.MessageManager mm = plugin.getMessageManager();
            String warnMsg = mm.colorize(mm.getMessage("punishments.warn.player-message")
                    .replace("%reason%", reason)
                    .replace("%staff%", punisher)
                    .replace("%count%", String.valueOf(warnings)));
            player.sendMessage(warnMsg);

            // Uyarı sayısını kaydet
            playerWarnings.put(playerName, warnings);

            // 3 uyarı = otomatik ban
            if (warnings >= 3) {
                playerWarnings.remove(playerName);
                ban(playerName, "3 uyarı aldınız (Otomatik ban)", "SYSTEM", 86400000); // 1 gün
            }

            return true;
        }
        return false;
    }

    @Override
    public boolean unban(String playerName) {
        return sendUnpunishmentRequest(playerName, "unban");
    }

    @Override
    public boolean unmute(String playerName) {
        return sendUnpunishmentRequest(playerName, "unmute");
    }

    @Override
    public boolean isBanned(String playerName) {
        return checkPunishment(playerName, "ban");
    }

    @Override
    public boolean isMuted(String playerName) {
        return checkPunishment(playerName, "mute");
    }

    /**
     * Ana thread'i bekletmez: son bilinen değeri döndürür, arka planda proxy'den tazeleme ister.
     * İlk sorguda (önbellek boşken) false döner.
     */
    private boolean checkPunishment(String playerName, String type) {
        String requestId = UUID.randomUUID().toString();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CheckPunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF(type);

        byte[] data = out.toByteArray();
        Runnable refresh = () -> {
            if (tracker.trackCheck(requestId, type, playerName) && !tracker.send(data)) {
                tracker.abort(requestId);
            }
        };
        // Async thread'den (örn. chat event) çağrılırsa tazeleme ana thread'e devredilir; cevap yine önbellekten
        if (Bukkit.isPrimaryThread()) {
            refresh.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, refresh);
        }
        return tracker.cached(type, playerName);
    }

    public int getWarningCount(String playerName) {
        return playerWarnings.getOrDefault(playerName, 0);
    }

    /** Bekletmez: istek gönderilince true ("proxy'ye iletildi"); sonuç/zaman aşımı yetkiliye asenkron bildirilir. */
    private boolean sendPunishmentRequest(String playerName, String type, String reason, String punisher, long duration) {
        if (!Bukkit.isPrimaryThread()) {
            // Async çağrı: plugin message yalnız ana thread'den gönderilir; "istek iletildi" kabul edilir
            Bukkit.getScheduler().runTask(plugin, () -> sendPunishmentRequest(playerName, type, reason, punisher, duration));
            return true;
        }
        String requestId = UUID.randomUUID().toString();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GlobalPunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF(type);
        out.writeUTF(reason);
        out.writeUTF(punisher);
        out.writeLong(duration);

        tracker.trackAction(requestId, type, playerName, punisher);
        if (!tracker.send(out.toByteArray())) {
            plugin.getLogger().warning("No online players to send punishment request!");
            tracker.abort(requestId);
            return false;
        }
        return true;
    }

    private boolean sendUnpunishmentRequest(String playerName, String type) {
        if (!Bukkit.isPrimaryThread()) {
            // Async çağrı: plugin message yalnız ana thread'den gönderilir; "istek iletildi" kabul edilir
            Bukkit.getScheduler().runTask(plugin, () -> sendUnpunishmentRequest(playerName, type));
            return true;
        }
        String requestId = UUID.randomUUID().toString();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GlobalUnpunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF(type);

        tracker.trackAction(requestId, type, playerName, null);
        if (!tracker.send(out.toByteArray())) {
            tracker.abort(requestId);
            return false;
        }
        return true;
    }

    // BungeeCord'dan gelen cevapları işle (ana thread)
    public void handlePunishmentResult(String requestId, boolean success) {
        tracker.complete(requestId, success);
    }

    // BungeeCord'dan gelen kontrol cevaplarını işle (ana thread)
    public void handleCheckResult(String requestId, boolean result) {
        tracker.complete(requestId, result);
    }
}
