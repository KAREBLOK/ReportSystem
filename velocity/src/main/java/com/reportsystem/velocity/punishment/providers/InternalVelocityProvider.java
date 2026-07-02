package com.reportsystem.velocity.punishment.providers;

import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.punishment.VelocityPunishmentManager.PunishmentProvider;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class InternalVelocityProvider implements PunishmentProvider {

    private final ReportSystemVelocity plugin;
    private final Map<String, Long> mutedPlayers = new ConcurrentHashMap<>();

    public InternalVelocityProvider(ReportSystemVelocity plugin) {
        this.plugin = plugin;

        // Mute listesini temizleme task'i (her dakika)
        plugin.getServer().getScheduler().buildTask(plugin, this::cleanupExpiredMutes)
                .repeat(1L, TimeUnit.MINUTES)
                .schedule();
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        // Dahili ban sistemi - veritabani kullanir
        // VelocityPunishmentManager zaten veritabanina kaydediyor
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        if (duration > 0) {
            mutedPlayers.put(playerName.toLowerCase(), System.currentTimeMillis() + duration);
        } else {
            mutedPlayers.put(playerName.toLowerCase(), Long.MAX_VALUE); // Kalici mute
        }
        return true;
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        return plugin.getServer().getPlayer(playerName).map(player -> {
            String msg = plugin.getConfig().getMessage("kick.player-message",
                    "&cSunucudan atildiniz!\n\n&7Sebep: &f%reason%\n&7Atan yetkili: &f%staff%\n\n&aTekrar girebilirsiniz.",
                    "%reason%", reason, "%staff%", punisher);
            player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
            return true;
        }).orElse(false);
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        // Uyariyi veritabanina kaydet (VelocityPunishmentManager hallediyor)
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        // Veritabanindan kaldirilacak (VelocityPunishmentManager hallediyor)
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        mutedPlayers.remove(playerName.toLowerCase());
        return true;
    }

    @Override
    public boolean isBanned(String playerName) {
        // VelocityPunishmentManager veritabani kontrolu yapacak
        return false;
    }

    @Override
    public boolean isMuted(String playerName) {
        Long muteEnd = mutedPlayers.get(playerName.toLowerCase());
        if (muteEnd == null) return false;

        if (muteEnd == Long.MAX_VALUE) return true; // Kalici mute

        if (System.currentTimeMillis() < muteEnd) {
            return true;
        } else {
            mutedPlayers.remove(playerName.toLowerCase());
            return false;
        }
    }

    /**
     * Suresi dolmus mute'lari temizler
     */
    private void cleanupExpiredMutes() {
        long now = System.currentTimeMillis();
        mutedPlayers.entrySet().removeIf(entry -> {
            Long muteEnd = entry.getValue();
            return muteEnd != Long.MAX_VALUE && muteEnd < now;
        });
    }
}
