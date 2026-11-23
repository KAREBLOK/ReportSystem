package com.reportsystem.velocity.punishment.providers;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.common.punishment.PunishmentCommandSanitizer;
import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.punishment.VelocityPunishmentManager.PunishmentProvider;

public class LiteBansVelocityProvider implements PunishmentProvider {

    private final ReportSystemVelocity plugin;

    public LiteBansVelocityProvider(ReportSystemVelocity plugin) {
        this.plugin = plugin;
    }

    private void dispatchCommand(String command) {
        plugin.getServer().getCommandManager().executeAsync(
                plugin.getServer().getConsoleCommandSource(), command
        );
    }

    /** Geçersiz oyuncu adı: komut ÇALIŞTIRILMAZ (ad silinip başka oyuncuya dönüşmesin) [CRA-02]. */
    private boolean rejectName(String playerName) {
        plugin.getLogger().warn("[LiteBans] Invalid player name rejected, command not sent: {}", PunishmentCommandSanitizer.logSafe(playerName));
        return false;
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        if (duration > 0) {
            dispatchCommand(PunishmentCommandSanitizer.command("litebans:tempban", safePlayer, DurationFormat.toLiteBans(duration), safeReason, "-s"));
        } else {
            dispatchCommand(PunishmentCommandSanitizer.command("litebans:ban", safePlayer, safeReason, "-s"));
        }
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        if (duration > 0) {
            dispatchCommand(PunishmentCommandSanitizer.command("litebans:tempmute", safePlayer, DurationFormat.toLiteBans(duration), safeReason, "-s"));
        } else {
            dispatchCommand(PunishmentCommandSanitizer.command("litebans:mute", safePlayer, safeReason, "-s"));
        }
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:kick", safePlayer, PunishmentCommandSanitizer.reason(reason)));
        return true;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:warn", safePlayer, PunishmentCommandSanitizer.reason(reason)));
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:unban", safePlayer));
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        dispatchCommand(PunishmentCommandSanitizer.command("litebans:unmute", safePlayer));
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
