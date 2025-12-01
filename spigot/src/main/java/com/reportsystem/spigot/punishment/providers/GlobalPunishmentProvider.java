package com.reportsystem.spigot.punishment.providers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.common.punishment.PunishmentProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GlobalPunishmentProvider implements PunishmentProvider {

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingRequests = new ConcurrentHashMap<>();

    public GlobalPunishmentProvider(JavaPlugin plugin) {
        this.plugin = plugin;
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
     * BungeeCord'a ceza isteği gönderir
     */
    private boolean sendPunishmentRequest(String type, String playerName, String reason,
                                          String punisher, long duration) {
        String requestId = UUID.randomUUID().toString();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GlobalPunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF(type);
        out.writeUTF(reason);
        out.writeUTF(punisher);
        out.writeLong(duration);

        // İlk online oyuncuyu bul
        Player messageSender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (messageSender == null) {
            plugin.getLogger().warning("No online player to send punishment request!");
            return false;
        }

        // Future oluştur ve bekle
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        // Mesajı gönder
        messageSender.sendPluginMessage(plugin, "reportsystem:channel", out.toByteArray());

        try {
            // 5 saniye timeout ile sonucu bekle
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Punishment request timeout or error: " + e.getMessage());
            pendingRequests.remove(requestId);
            return false;
        }
    }

    /**
     * BungeeCord'a ceza kaldırma isteği gönderir
     */
    private boolean sendUnpunishmentRequest(String type, String playerName) {
        String requestId = UUID.randomUUID().toString();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GlobalUnpunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF(type);

        Player messageSender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (messageSender == null) {
            plugin.getLogger().warning("No online player to send unpunishment request!");
            return false;
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        messageSender.sendPluginMessage(plugin, "reportsystem:channel", out.toByteArray());

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Unpunishment request timeout or error: " + e.getMessage());
            pendingRequests.remove(requestId);
            return false;
        }
    }

    /**
     * Ceza durumunu kontrol eder
     */
    private boolean checkPunishment(String playerName, String type) {
        String requestId = UUID.randomUUID().toString();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CheckPunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF(type);

        Player messageSender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (messageSender == null) {
            return false;
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        messageSender.sendPluginMessage(plugin, "reportsystem:channel", out.toByteArray());

        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return false;
        }
    }

    /**
     * BungeeCord'dan gelen cevabı işler
     */
    public void handleResponse(String requestId, boolean result) {
        CompletableFuture<Boolean> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.complete(result);
        }
    }
}