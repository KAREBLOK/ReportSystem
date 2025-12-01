package com.reportsystem.spigot.punishment;

import com.reportsystem.common.punishment.PunishmentProvider;
import com.reportsystem.spigot.punishment.providers.*;
import org.bukkit.plugin.java.JavaPlugin;

public class PunishmentManager {
    private final JavaPlugin plugin;
    private final PunishmentProvider provider;

    public PunishmentManager(JavaPlugin plugin) {
        this.plugin = plugin;

        // BungeeCord kullanılıyor mu kontrol et
        boolean useBungeeCord = plugin.getConfig().getBoolean("use-bungeecord", true);

        if (useBungeeCord) {
            // BungeeCord üzerinden global ceza sistemi
            this.provider = new GlobalPunishmentProvider(plugin);
            plugin.getLogger().info("Global ceza sistemi (BungeeCord) kullanılıyor.");
        } else {
            // Local ceza sistemi
            this.provider = new InternalPunishmentProvider(plugin);
            plugin.getLogger().info("Dahili ceza sistemi kullanılıyor.");
        }
    }

    public PunishmentProvider getProvider() {
        return provider;
    }

    // Süre parse etme helper metodu
    public static long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) return -1;

        try {
            // Eğer sadece sayıysa, dakika olarak kabul et
            if (duration.matches("\\d+")) {
                return Long.parseLong(duration) * 60 * 1000;
            }

            // Format: 1d, 2h, 30m, etc.
            String time = duration.substring(0, duration.length() - 1);
            char unit = duration.charAt(duration.length() - 1);
            long value = Long.parseLong(time);

            switch (Character.toLowerCase(unit)) {
                case 's': return value * 1000;
                case 'm': return value * 60 * 1000;
                case 'h': return value * 60 * 60 * 1000;
                case 'd': return value * 24 * 60 * 60 * 1000;
                case 'w': return value * 7 * 24 * 60 * 60 * 1000;
                case 'y': return value * 365 * 24 * 60 * 60 * 1000;
                default: return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }
}