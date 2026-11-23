package com.reportsystem.bungee.punishment.providers;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.common.punishment.PunishmentCommandSanitizer;
import com.reportsystem.bungee.punishment.BungeePunishmentManager.PunishmentProvider;
import net.md_5.bungee.api.ProxyServer;

public class LiteBansBungeeProvider implements PunishmentProvider {

    /** Geçersiz oyuncu adı: komut ÇALIŞTIRILMAZ (ad silinip başka oyuncuya dönüşmesin) [CRA-02]. */
    private boolean rejectName(String playerName) {
        ProxyServer.getInstance().getLogger().warning("[LiteBans] Invalid player name rejected, command not sent: " + PunishmentCommandSanitizer.logSafe(playerName));
        return false;
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        String safeReason = PunishmentCommandSanitizer.reason(reason);
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        String command;
        if (duration > 0) {
            // Süreyi LiteBans formatına çevir
            String timeString = DurationFormat.toLiteBans(duration);
            command = PunishmentCommandSanitizer.command("litebans:tempban", safePlayer, timeString, safeReason, "-s");
        } else {
            command = PunishmentCommandSanitizer.command("litebans:ban", safePlayer, safeReason, "-s");
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
            String timeString = DurationFormat.toLiteBans(duration);
            command = PunishmentCommandSanitizer.command("litebans:tempmute", safePlayer, timeString, safeReason, "-s");
        } else {
            command = PunishmentCommandSanitizer.command("litebans:mute", safePlayer, safeReason, "-s");
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
        String command = PunishmentCommandSanitizer.command("litebans:kick", safePlayer, safeReason);
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
        String command = PunishmentCommandSanitizer.command("litebans:warn", safePlayer, safeReason);
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
                ProxyServer.getInstance().getConsole(), PunishmentCommandSanitizer.command("litebans:unban", safePlayer)
        );
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        String safePlayer = PunishmentCommandSanitizer.name(playerName);
        if (safePlayer == null) return rejectName(playerName);
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), PunishmentCommandSanitizer.command("litebans:unmute", safePlayer)
        );
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
