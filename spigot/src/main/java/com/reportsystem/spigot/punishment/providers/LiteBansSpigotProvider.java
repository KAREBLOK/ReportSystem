package com.reportsystem.spigot.punishment.providers;

import com.reportsystem.common.punishment.PunishmentProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class LiteBansSpigotProvider implements PunishmentProvider {

    private final JavaPlugin plugin;

    public LiteBansSpigotProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private String sanitizeInput(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9\u00e7\u00c7\u011f\u011e\u0131\u0130\u00f6\u00d6\u015f\u015e\u00fc\u00dc .,_\\-]", "").trim();
    }

    private void dispatchCommand(String command) {
        // Main thread'de çalıştır
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long durationMillis) {
        if (durationMillis > 0) {
            return tempBan(playerName, reason, punisher, durationMillis);
        } else {
            return permBan(playerName, reason, punisher);
        }
    }

    @Override
    public boolean tempBan(String playerName, String reason, String punisher, long durationMillis) {
        String safePlayer = sanitizeInput(playerName);
        String safeReason = sanitizeInput(reason);
        String timeString = convertDuration(durationMillis);
        dispatchCommand(String.format("litebans:tempban %s %s %s -s", safePlayer, timeString, safeReason));
        plugin.getLogger().info("[LiteBans] TempBan: " + playerName + " by " + punisher + " for " + timeString);
        return true;
    }

    @Override
    public boolean permBan(String playerName, String reason, String punisher) {
        String safePlayer = sanitizeInput(playerName);
        String safeReason = sanitizeInput(reason);
        dispatchCommand(String.format("litebans:ban %s %s -s", safePlayer, safeReason));
        plugin.getLogger().info("[LiteBans] PermBan: " + playerName + " by " + punisher);
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long durationMillis) {
        String safePlayer = sanitizeInput(playerName);
        String safeReason = sanitizeInput(reason);
        if (durationMillis > 0) {
            String timeString = convertDuration(durationMillis);
            dispatchCommand(String.format("litebans:tempmute %s %s %s -s", safePlayer, timeString, safeReason));
        } else {
            dispatchCommand(String.format("litebans:mute %s %s -s", safePlayer, safeReason));
        }
        plugin.getLogger().info("[LiteBans] Mute: " + playerName + " by " + punisher);
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        String safePlayer = sanitizeInput(playerName);
        String safeReason = sanitizeInput(reason);
        dispatchCommand(String.format("litebans:kick %s %s", safePlayer, safeReason));
        plugin.getLogger().info("[LiteBans] Kick: " + playerName + " by " + punisher);
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        String safePlayer = sanitizeInput(playerName);
        String safeReason = sanitizeInput(reason);
        dispatchCommand(String.format("litebans:warn %s %s", safePlayer, safeReason));
        plugin.getLogger().info("[LiteBans] Warn: " + playerName + " by " + punisher);
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        String safePlayer = sanitizeInput(playerName);
        dispatchCommand("litebans:unban " + safePlayer);
        plugin.getLogger().info("[LiteBans] Unban: " + playerName);
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        String safePlayer = sanitizeInput(playerName);
        dispatchCommand("litebans:unmute " + safePlayer);
        plugin.getLogger().info("[LiteBans] Unmute: " + playerName);
        return true;
    }

    @Override
    public boolean isBanned(String playerName) {
        // LiteBans kendi login kontrolünü yapıyor
        return false;
    }

    @Override
    public boolean isMuted(String playerName) {
        // LiteBans kendi mute kontrolünü yapıyor
        return false;
    }

    private String convertDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}
