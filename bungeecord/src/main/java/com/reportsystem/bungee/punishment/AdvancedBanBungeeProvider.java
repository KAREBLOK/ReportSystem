package com.reportsystem.bungee.punishment.providers;

import com.reportsystem.bungee.punishment.BungeePunishmentManager.PunishmentProvider;
import net.md_5.bungee.api.ProxyServer;

public class AdvancedBanBungeeProvider implements PunishmentProvider {

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        String command;
        if (duration > 0) {
            // AdvancedBan süre formatı: s, m, h, d, w, mo, y
            String timeString = convertDuration(duration);
            command = String.format("aban tempban %s %s %s", playerName, timeString, reason);
        } else {
            command = String.format("aban ban %s %s", playerName, reason);
        }

        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        String command;
        if (duration > 0) {
            String timeString = convertDuration(duration);
            command = String.format("aban tempmute %s %s %s", playerName, timeString, reason);
        } else {
            command = String.format("aban mute %s %s", playerName, reason);
        }

        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        String command = String.format("aban kick %s %s", playerName, reason);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        String command = String.format("aban warn %s %s", playerName, reason);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), "aban unban " + playerName
        );
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), "aban unmute " + playerName
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