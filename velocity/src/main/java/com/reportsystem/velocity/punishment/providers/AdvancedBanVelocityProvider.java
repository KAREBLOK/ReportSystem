package com.reportsystem.velocity.punishment.providers;

import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.punishment.VelocityPunishmentManager.PunishmentProvider;

public class AdvancedBanVelocityProvider implements PunishmentProvider {

    private final ReportSystemVelocity plugin;

    public AdvancedBanVelocityProvider(ReportSystemVelocity plugin) {
        this.plugin = plugin;
    }

    private String sanitizeInput(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9\u00e7\u00c7\u011f\u011e\u0131\u0130\u00f6\u00d6\u015f\u015e\u00fc\u00dc .,_\\-]", "").trim();
    }

    private void dispatchCommand(String command) {
        plugin.getServer().getCommandManager().executeAsync(
                plugin.getServer().getConsoleCommandSource(), command
        );
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        String safeReason = sanitizeInput(reason);
        String safePlayer = sanitizeInput(playerName);
        if (duration > 0) {
            dispatchCommand(String.format("aban tempban %s %s %s", safePlayer, convertDuration(duration), safeReason));
        } else {
            dispatchCommand(String.format("aban ban %s %s", safePlayer, safeReason));
        }
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        String safeReason = sanitizeInput(reason);
        String safePlayer = sanitizeInput(playerName);
        if (duration > 0) {
            dispatchCommand(String.format("aban tempmute %s %s %s", safePlayer, convertDuration(duration), safeReason));
        } else {
            dispatchCommand(String.format("aban mute %s %s", safePlayer, safeReason));
        }
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        dispatchCommand(String.format("aban kick %s %s", sanitizeInput(playerName), sanitizeInput(reason)));
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        dispatchCommand(String.format("aban warn %s %s", sanitizeInput(playerName), sanitizeInput(reason)));
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        dispatchCommand("aban unban " + sanitizeInput(playerName));
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        dispatchCommand("aban unmute " + sanitizeInput(playerName));
        return true;
    }

    @Override
    public boolean isBanned(String playerName) {
        // AdvancedBan API entegrasyonu eklenebilir
        return false;
    }

    @Override
    public boolean isMuted(String playerName) {
        // AdvancedBan API entegrasyonu eklenebilir
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
