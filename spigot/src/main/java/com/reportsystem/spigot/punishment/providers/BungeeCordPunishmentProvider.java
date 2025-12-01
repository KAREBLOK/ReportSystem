package com.reportsystem.spigot.punishment.providers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.common.punishment.PunishmentProvider;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BungeeCordPunishmentProvider implements PunishmentProvider {

    private final ReportSystemSpigot plugin;
    private final Map<String, CompletableFuture<Boolean>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, Integer> playerWarnings = new HashMap<>();

    public BungeeCordPunishmentProvider(ReportSystemSpigot plugin) {
        this.plugin = plugin;
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
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.DARK_RED + "════════════════════════════");
            player.sendMessage(ChatColor.RED + "           ⚠ UYARI ⚠");
            player.sendMessage(ChatColor.DARK_RED + "════════════════════════════");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + reason);
            player.sendMessage(ChatColor.GRAY + "Uyarı veren: " + ChatColor.WHITE + punisher);
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "Kuralları ihlal etmeye devam ederseniz");
            player.sendMessage(ChatColor.RED + "daha ağır cezalar alabilirsiniz!");
            player.sendMessage(ChatColor.DARK_RED + "════════════════════════════");
            player.sendMessage("");

            // Uyarı sayısını artır
            int warnings = playerWarnings.getOrDefault(playerName, 0) + 1;
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
        // BungeeCord'a sorgu gönder ve cevap bekle
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, future);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CheckPunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF("ban");

        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) return false;

        sender.sendPluginMessage(plugin, "reportsystem:channel", out.toByteArray());

        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return false;
        }
    }

    @Override
    public boolean isMuted(String playerName) {
        // BungeeCord'a sorgu gönder ve cevap bekle
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, future);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CheckPunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF("mute");

        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) return false;

        sender.sendPluginMessage(plugin, "reportsystem:channel", out.toByteArray());

        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return false;
        }
    }

    public int getWarningCount(String playerName) {
        return playerWarnings.getOrDefault(playerName, 0);
    }

    private boolean sendPunishmentRequest(String playerName, String type, String reason, String punisher, long duration) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, future);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GlobalPunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF(type);
        out.writeUTF(reason);
        out.writeUTF(punisher);
        out.writeLong(duration);

        // İlk online oyuncuyu bul ve mesajı gönder
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            plugin.getLogger().warning("No online players to send punishment request!");
            pendingRequests.remove(requestId);
            return false;
        }

        sender.sendPluginMessage(plugin, "reportsystem:channel", out.toByteArray());

        try {
            boolean result = future.get(5, TimeUnit.SECONDS);
            plugin.getLogger().info("[BUNGEECORD] Punishment request result: " + result);
            return result;
        } catch (TimeoutException e) {
            plugin.getLogger().warning("[BUNGEECORD] Punishment request timeout for " + playerName);
            pendingRequests.remove(requestId);
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[BUNGEECORD] Punishment request error", e);
            pendingRequests.remove(requestId);
            return false;
        }
    }

    private boolean sendUnpunishmentRequest(String playerName, String type) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, future);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GlobalUnpunishment");
        out.writeUTF(requestId);
        out.writeUTF(playerName);
        out.writeUTF(type);

        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            pendingRequests.remove(requestId);
            return false;
        }

        sender.sendPluginMessage(plugin, "reportsystem:channel", out.toByteArray());

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return false;
        }
    }

    // BungeeCord'dan gelen cevapları işle
    public void handlePunishmentResult(String requestId, boolean success) {
        CompletableFuture<Boolean> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.complete(success);
        }
    }

    // BungeeCord'dan gelen kontrol cevaplarını işle
    public void handleCheckResult(String requestId, boolean result) {
        CompletableFuture<Boolean> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.complete(result);
        }
    }
}