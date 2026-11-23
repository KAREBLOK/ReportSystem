package com.reportsystem.spigot.punishment.providers;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.common.punishment.PunishmentCommandSanitizer;
import com.reportsystem.common.punishment.PunishmentProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class LiteBansSpigotProvider implements PunishmentProvider {

    private final JavaPlugin plugin;

    public LiteBansSpigotProvider(JavaPlugin plugin) {
        this.plugin = plugin;
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

    /** Geçersiz oyuncu adı: komut ÇALIŞTIRILMAZ (ad silinip başka oyuncuya dönüşmesin) [CRA-02]. */
    private boolean rejectName(String playerName) {
        plugin.getLogger().warning("[LiteBans] Invalid player name rejected, command not sent: " + PunishmentCommandSanitizer.logSafe(playerName));
        return false;
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
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String timeString = DurationFormat.toLiteBans(durationMillis);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:tempban", safePlayer, timeString, safeReason, "-s"));
        plugin.getLogger().info("[LiteBans] TempBan: " + playerName + " by " + punisher + " for " + timeString);
        return true;
    }

    @Override
    public boolean permBan(String playerName, String reason, String punisher) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:ban", safePlayer, safeReason, "-s"));
        plugin.getLogger().info("[LiteBans] PermBan: " + playerName + " by " + punisher);
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long durationMillis) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        if (durationMillis > 0) {
            String timeString = DurationFormat.toLiteBans(durationMillis);
            dispatchCommand(PunishmentCommandSanitizer.command("litebans:tempmute", safePlayer, timeString, safeReason, "-s"));
        } else {
            dispatchCommand(PunishmentCommandSanitizer.command("litebans:mute", safePlayer, safeReason, "-s"));
        }
        plugin.getLogger().info("[LiteBans] Mute: " + playerName + " by " + punisher);
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:kick", safePlayer, safeReason));
        plugin.getLogger().info("[LiteBans] Kick: " + playerName + " by " + punisher);
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:warn", safePlayer, safeReason));
        plugin.getLogger().info("[LiteBans] Warn: " + playerName + " by " + punisher);
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:unban", safePlayer));
        plugin.getLogger().info("[LiteBans] Unban: " + playerName);
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:unmute", safePlayer));
        plugin.getLogger().info("[LiteBans] Unmute: " + playerName);
        return true;
    }

    @Override
    public boolean isBanned(String playerName) {
        // LiteBans cezayı kendisi uygular; API bağımlılığı bilinçli olarak eklenmedi (H-12)
        return false;
    }

    @Override
    public boolean isMuted(String playerName) {
        // LiteBans cezayı kendisi uygular; API bağımlılığı bilinçli olarak eklenmedi (H-12)
        return false;
    }

}
