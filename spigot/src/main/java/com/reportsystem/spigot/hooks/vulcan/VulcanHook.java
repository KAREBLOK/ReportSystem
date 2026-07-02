package com.reportsystem.spigot.hooks.vulcan;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;

/**
 * Vulcan Anti-Cheat hook.
 * Standart Bukkit eventleri kullanir (VulcanFlagEvent, VulcanPunishEvent).
 * Vulcan yuklu degilse bu sinif hic yuklenmez (softdepend).
 */
public class VulcanHook {

    private final ReportSystemSpigot plugin;
    private VulcanDetectionListener listener;
    private boolean connected = false;

    public VulcanHook(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            Class.forName("me.frep.vulcan.api.event.VulcanFlagEvent");

            this.listener = new VulcanDetectionListener(plugin);
            Bukkit.getPluginManager().registerEvents(listener, plugin);

            connected = true;
            plugin.getLogger().info("[VULCAN] Vulcan Anti-Cheat entegrasyonu aktif!");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[VULCAN] Vulcan API sinifi bulunamadi. " +
                    "Vulcan config'de enable-api: true mi?");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("[VULCAN] Vulcan baglanti hatasi: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        if (listener != null) {
            listener.shutdown();
            listener = null;
        }
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }
}
