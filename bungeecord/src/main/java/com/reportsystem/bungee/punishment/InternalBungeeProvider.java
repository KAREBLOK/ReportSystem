package com.reportsystem.bungee.punishment.providers;

import com.reportsystem.bungee.ReportSystemBungee;
import com.reportsystem.bungee.punishment.BungeePunishmentManager.PunishmentProvider;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class InternalBungeeProvider implements PunishmentProvider {

    private final ReportSystemBungee plugin;
    private final Map<String, Long> mutedPlayers = new ConcurrentHashMap<>();

    public InternalBungeeProvider(ReportSystemBungee plugin) {
        this.plugin = plugin;

        // Mute listesini temizleme task'ı (her dakika)
        ProxyServer.getInstance().getScheduler().schedule(plugin, this::cleanupExpiredMutes, 0L, 1L, TimeUnit.MINUTES);
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        // Dahili ban sistemi - veritabanı kullanır
        // BungeePunishmentManager zaten veritabanına kaydediyor
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        if (duration > 0) {
            mutedPlayers.put(playerName.toLowerCase(), System.currentTimeMillis() + duration);
        } else {
            mutedPlayers.put(playerName.toLowerCase(), Long.MAX_VALUE); // Kalıcı mute
        }
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        if (player != null) {
            String kickMsg = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.kick.player-message",
                            "&cSunucudan atıldınız!\n\n&7Sebep: &f%reason%\n&7Atan yetkili: &f%staff%\n\n&aTekrar girebilirsiniz."))
                    .replace("%reason%", reason)
                    .replace("%staff%", punisher);
            player.disconnect(kickMsg);
            return true;
        }
        return false;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        // Uyarıyı veritabanına kaydet (BungeePunishmentManager hallediyor)
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        // Veritabanından kaldırılacak (BungeePunishmentManager hallediyor)
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        mutedPlayers.remove(playerName.toLowerCase());
        return true;
    }

    @Override
    public boolean isBanned(String playerName) {
        // BungeePunishmentManager veritabanı kontrolü yapacak
        return false;
    }

    @Override
    public boolean isMuted(String playerName) {
        Long muteEnd = mutedPlayers.get(playerName.toLowerCase());
        if (muteEnd == null) return false;

        if (muteEnd == Long.MAX_VALUE) return true; // Kalıcı mute

        if (System.currentTimeMillis() < muteEnd) {
            return true;
        } else {
            mutedPlayers.remove(playerName.toLowerCase());
            return false;
        }
    }

    /**
     * Süresi dolmuş mute'ları temizler
     */
    private void cleanupExpiredMutes() {
        long now = System.currentTimeMillis();
        mutedPlayers.entrySet().removeIf(entry -> {
            Long muteEnd = entry.getValue();
            return muteEnd != Long.MAX_VALUE && muteEnd < now;
        });
    }
}