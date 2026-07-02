package com.reportsystem.bungee.punishment.providers;

import com.reportsystem.bungee.punishment.BungeePunishmentManager.PunishmentProvider;
import net.md_5.bungee.api.ProxyServer;

public class LiteBansBungeeProvider implements PunishmentProvider {

    /**
     * Komut enjeksiyonunu önlemek için input string'ini temizler.
     * Sadece alfanumerik, boşluk ve temel noktalama işaretlerine izin verir.
     */
    private String sanitizeInput(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ .,_\\-]", "").trim();
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        String safeReason = sanitizeInput(reason);
        String safePlayer = sanitizeInput(playerName);
        String command;
        if (duration > 0) {
            // Süreyi LiteBans formatına çevir
            String timeString = convertDuration(duration);
            command = String.format("litebans:tempban %s %s %s -s", safePlayer, timeString, safeReason);
        } else {
            command = String.format("litebans:ban %s %s -s", safePlayer, safeReason);
        }

        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        String safeReason = sanitizeInput(reason);
        String safePlayer = sanitizeInput(playerName);
        String command;
        if (duration > 0) {
            String timeString = convertDuration(duration);
            command = String.format("litebans:tempmute %s %s %s -s", safePlayer, timeString, safeReason);
        } else {
            command = String.format("litebans:mute %s %s -s", safePlayer, safeReason);
        }

        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        String safeReason = sanitizeInput(reason);
        String safePlayer = sanitizeInput(playerName);
        String command = String.format("litebans:kick %s %s", safePlayer, safeReason);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        String safeReason = sanitizeInput(reason);
        String safePlayer = sanitizeInput(playerName);
        String command = String.format("litebans:warn %s %s", safePlayer, safeReason);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command
        );
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        String safePlayer = sanitizeInput(playerName);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), "litebans:unban " + safePlayer
        );
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        String safePlayer = sanitizeInput(playerName);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), "litebans:unmute " + safePlayer
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