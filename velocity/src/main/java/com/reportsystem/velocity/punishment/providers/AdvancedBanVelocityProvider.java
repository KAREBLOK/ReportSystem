package com.reportsystem.velocity.punishment.providers;

import com.reportsystem.common.punishment.PunishmentCommandSanitizer;
import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.punishment.VelocityPunishmentManager.PunishmentProvider;

public class AdvancedBanVelocityProvider implements PunishmentProvider {

    private final ReportSystemVelocity plugin;

    public AdvancedBanVelocityProvider(ReportSystemVelocity plugin) {
        this.plugin = plugin;
    }

    private void dispatchCommand(String command) {
        plugin.getServer().getCommandManager().executeAsync(
                plugin.getServer().getConsoleCommandSource(), command
        );
    }

    /** Geçersiz oyuncu adı: komut ÇALIŞTIRILMAZ (ad silinip başka oyuncuya dönüşmesin) [CRA-02]. */
    private boolean rejectName(String playerName) {
        plugin.getLogger().warn("[AdvancedBan] Invalid player name rejected, command not sent: {}", PunishmentCommandSanitizer.logSafe(playerName));
        return false;
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        if (duration > 0) {
            dispatchCommand(PunishmentCommandSanitizer.command("aban tempban", safePlayer, convertDuration(duration), safeReason));
        } else {
            dispatchCommand(PunishmentCommandSanitizer.command("aban ban", safePlayer, safeReason));
        }
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        if (duration > 0) {
            dispatchCommand(PunishmentCommandSanitizer.command("aban tempmute", safePlayer, convertDuration(duration), safeReason));
        } else {
            dispatchCommand(PunishmentCommandSanitizer.command("aban mute", safePlayer, safeReason));
        }
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("aban kick", safePlayer, PunishmentCommandSanitizer.reason(reason)));
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("aban warn", safePlayer, PunishmentCommandSanitizer.reason(reason)));
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("aban unban", safePlayer));
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("aban unmute", safePlayer));
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
