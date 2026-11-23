package com.reportsystem.spigot.punishment;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.common.punishment.PunishmentProvider;
import com.reportsystem.spigot.punishment.providers.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PunishmentManager {
    private final JavaPlugin plugin;
    private final PunishmentProvider provider;

    public PunishmentManager(JavaPlugin plugin) {
        this.plugin = plugin;

        // BungeeCord kullanılıyor mu kontrol et
        boolean useBungeeCord = plugin.getConfig().getBoolean("bungeecord.enabled", false);

        // LiteBans yüklü mü kontrol et
        boolean liteBansInstalled = Bukkit.getPluginManager().getPlugin("LiteBans") != null;

        if (liteBansInstalled) {
            // LiteBans üzerinden ceza sistemi
            this.provider = new LiteBansSpigotProvider(plugin);
            plugin.getLogger().info("LiteBans ceza sistemi kullanılıyor.");
        } else if (useBungeeCord) {
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

    /**
     * Yalnız dahili sağlayıcıda: hedef oyuncu çevrimiçi/önbellekte yoksa (HTTP'siz) ban uygulanamaz.
     * Diğer sağlayıcılar (LiteBans, global) oyuncuyu kendisi çözer -> false.
     */
    public boolean isUnknownBanTarget(String playerName) {
        return provider instanceof InternalPunishmentProvider
                && !((InternalPunishmentProvider) provider).isKnownPlayer(playerName);
    }

    // Süre parse etme helper metodu
    public static long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) return -1;

        try {
            // Eğer sadece sayıysa, dakika olarak kabul et
            // T-1: üst sınırı (100 yıl) aşan süre kırpılır (bkz. DurationFormat.fromUnits)
            if (duration.matches("\\d+")) {
                return DurationFormat.fromUnits(Long.parseLong(duration), 60_000L);
            }

            // Format: 1d, 2h, 30m, etc.
            String time = duration.substring(0, duration.length() - 1);
            char unit = duration.charAt(duration.length() - 1);
            long value = Long.parseLong(time);

            switch (Character.toLowerCase(unit)) {
                case 's': return DurationFormat.fromUnits(value, 1000L);
                case 'm': return DurationFormat.fromUnits(value, 60_000L);
                case 'h': return DurationFormat.fromUnits(value, 3_600_000L);
                case 'd': return DurationFormat.fromUnits(value, 86_400_000L);
                case 'w': return DurationFormat.fromUnits(value, 604_800_000L);
                case 'y': return DurationFormat.fromUnits(value, 31_536_000_000L);
                default: return -1;
            }
        } catch (ArithmeticException e) {
            return -1; // negatif taşma: geçersiz süre
        } catch (Exception e) {
            return -1;
        }
    }
}