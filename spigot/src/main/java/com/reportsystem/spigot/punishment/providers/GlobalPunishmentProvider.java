package com.reportsystem.spigot.punishment.providers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.common.punishment.PunishmentProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class GlobalPunishmentProvider implements PunishmentProvider {

    private final JavaPlugin plugin;
    private final ProxyRequestTracker tracker;

    public GlobalPunishmentProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new ProxyRequestTracker(plugin);
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long durationMillis) {
        return sendPunishmentRequest("ban", playerName, reason, punisher, durationMillis);
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
        return sendPunishmentRequest("mute", playerName, reason, punisher, durationMillis);
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        return sendPunishmentRequest("kick", playerName, reason, punisher, 0);
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        return sendPunishmentRequest("warn", playerName, reason, punisher, 0);
    }

    @Override
    public boolean unban(String playerName) {
        return sendUnpunishmentRequest("unban", playerName);
    }

    @Override
    public boolean unmute(String playerName) {
        return sendUnpunishmentRequest("unmute", playerName);
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
     * BungeeCord'a ceza isteği gönderir. ANA THREAD'İ BEKLETMEZ: istek gönderilince true döner
     * ("istek proxy'ye iletildi"); proxy sonucu gelince/zaman aşımında yetkili bilgilendirilir.
     */
    private boolean sendPunishmentRequest(String type, String playerName, String reason,
                                          String punisher, long duration) {
        if (!Bukkit.isPrimaryThread()) {
            // Async çağrı: plugin message yalnız ana thread'den gönderilir; "istek iletildi" kabul edilir
            Bukkit.getScheduler().runTask(plugin, () -> sendPunishmentRequest(type, playerName, reason, punisher, duration));
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
            plugin.getLogger().warning("No online player to send punishment request!");
            tracker.abort(requestId);
            return false;
        }
        return true;
    }

    /**
     * BungeeCord'a ceza kaldırma isteği gönderir (bekletmez, bkz. sendPunishmentRequest).
     */
    private boolean sendUnpunishmentRequest(String type, String playerName) {
        if (!Bukkit.isPrimaryThread()) {
            // Async çağrı: plugin message yalnız ana thread'den gönderilir; "istek iletildi" kabul edilir
            Bukkit.getScheduler().runTask(plugin, () -> sendUnpunishmentRequest(type, playerName));
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
            plugin.getLogger().warning("No online player to send unpunishment request!");
            tracker.abort(requestId);
            return false;
        }
        return true;
    }

    /**
     * Ceza durumu: ana thread'i bekletmeden SON BİLİNEN değeri döndürür ve arka planda
     * proxy'den tazeleme ister. İlk sorguda (önbellek boşken) false döner; cevap sonraki çağrıda görünür.
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

    /**
     * BungeeCord'dan gelen cevabı işler (ana thread; hem eylem hem kontrol cevapları)
     */
    public void handleResponse(String requestId, boolean result) {
        tracker.complete(requestId, result);
    }
}
