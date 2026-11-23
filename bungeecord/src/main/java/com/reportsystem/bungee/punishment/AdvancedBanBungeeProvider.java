package com.reportsystem.bungee.punishment.providers;

import com.reportsystem.common.punishment.PunishmentCommandSanitizer;
import com.reportsystem.bungee.punishment.BungeePunishmentManager.PunishmentProvider;
import net.md_5.bungee.api.ProxyServer;

public class AdvancedBanBungeeProvider implements PunishmentProvider {

    /** Geçersiz oyuncu adı: komut ÇALIŞTIRILMAZ (ad silinip başka oyuncuya dönüşmesin) [CRA-02]. */
    private boolean rejectName(String playerName) {
        ProxyServer.getInstance().getLogger().warning("[AdvancedBan] Invalid player name rejected, command not sent: " + PunishmentCommandSanitizer.logSafe(playerName));
        return false;
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String command;
        if (duration > 0) {
            // AdvancedBan süre formatı: s, m, h, d, w, mo, y
            String timeString = convertDuration(duration);
            command = PunishmentCommandSanitizer.command("aban tempban", safePlayer, timeString, safeReason);
        } else {
            command = PunishmentCommandSanitizer.command("aban ban", safePlayer, safeReason);
        }

        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String command;
        if (duration > 0) {
            String timeString = convertDuration(duration);
            command = PunishmentCommandSanitizer.command("aban tempmute", safePlayer, timeString, safeReason);
        } else {
            command = PunishmentCommandSanitizer.command("aban mute", safePlayer, safeReason);
        }

        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String command = PunishmentCommandSanitizer.command("aban kick", safePlayer, safeReason);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String command = PunishmentCommandSanitizer.command("aban warn", safePlayer, safeReason);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), PunishmentCommandSanitizer.command("aban unban", safePlayer)
        );
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), PunishmentCommandSanitizer.command("aban unmute", safePlayer)
        );
        return true;
    }

    @Override
    public boolean isBanned(String playerName) {
        // AdvancedBan API kullanılabilirse daha iyi olur
        return false;
    }

    @Override
    public boolean isMuted(String playerName) {
        // AdvancedBan API kullanılabilirse daha iyi olur
        return false;
    }

    private String convertDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (years > 0) return years + "y";
        if (months > 0) return months + "mo";
        if (weeks > 0) return weeks + "w";
        if (days > 0) return days + "d";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}