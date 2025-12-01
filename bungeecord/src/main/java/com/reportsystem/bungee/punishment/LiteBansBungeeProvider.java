package com.reportsystem.bungee.punishment.providers;

import com.reportsystem.bungee.punishment.BungeePunishmentManager.PunishmentProvider;
import net.md_5.bungee.api.ProxyServer;

public class LiteBansBungeeProvider implements PunishmentProvider {

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        String command;
        if (duration > 0) {
            // Süreyi LiteBans formatına çevir
            String timeString = convertDuration(duration);
            command = String.format("litebans:tempban %s %s %s -s", playerName, timeString, reason);
        } else {
            command = String.format("litebans:ban %s %s -s", playerName, reason);
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
            command = String.format("litebans:tempmute %s %s %s -s", playerName, timeString, reason);
        } else {
            command = String.format("litebans:mute %s %s -s", playerName, reason);
        }

        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        String command = String.format("litebans:kick %s %s", playerName, reason);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        String command = String.format("litebans:warn %s %s", playerName, reason);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), "litebans:unban " + playerName
        );
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), "litebans:unmute " + playerName
        );
        return true;
    }

    @Override
    public boolean isBanned(String playerName) {
        // LiteBans API kullanılabilirse daha iyi olur
        // Şimdilik false dönüyoruz
        return false;
    }

    @Override
    public boolean isMuted(String playerName) {
        // LiteBans API kullanılabilirse daha iyi olur
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