package com.reportsystem.velocity.punishment.providers;

import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.punishment.VelocityPunishmentManager.PunishmentProvider;

public class LiteBansVelocityProvider implements PunishmentProvider {

    private final ReportSystemVelocity plugin;

    public LiteBansVelocityProvider(ReportSystemVelocity plugin) {
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
            dispatchCommand(String.format("litebans:tempban %s %s %s -s", safePlayer, convertDuration(duration), safeReason));
        } else {
            dispatchCommand(String.format("litebans:ban %s %s -s", safePlayer, safeReason));
        }
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        String safeReason = sanitizeInput(reason);
        String safePlayer = sanitizeInput(playerName);
        if (duration > 0) {
            dispatchCommand(String.format("litebans:tempmute %s %s %s -s", safePlayer, convertDuration(duration), safeReason));
        } else {
            dispatchCommand(String.format("litebans:mute %s %s -s", safePlayer, safeReason));
        }
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        dispatchCommand(String.format("litebans:kick %s %s", sanitizeInput(playerName), sanitizeInput(reason)));
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        dispatchCommand(String.format("litebans:warn %s %s", sanitizeInput(playerName), sanitizeInput(reason)));
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        dispatchCommand("litebans:unban " + sanitizeInput(playerName));
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        dispatchCommand("litebans:unmute " + sanitizeInput(playerName));
        return true;
    }

    @Override
    public boolean isBanned(String playerName) {
        // LiteBans API entegrasyonu eklenebilir
        return false;
    }

    @Override
    public boolean isMuted(String playerName) {
        // LiteBans API entegrasyonu eklenebilir
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
