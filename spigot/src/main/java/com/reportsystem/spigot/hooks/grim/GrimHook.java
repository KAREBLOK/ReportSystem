package com.reportsystem.spigot.hooks.grim;

import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

/**
 * Grim AntiCheat API hook.
 * Bukkit ServiceProvider ile API alinir, ozel event bus ile listener kaydedilir.
 * GrimAC yuklu degilse bu sinif hic yuklenmez (softdepend).
 */
public class GrimHook {

    private final ReportSystemSpigot plugin;
    private GrimAbstractAPI grimApi;
    private GrimDetectionListener listener;
    private boolean connected = false;
    private BukkitTask retryTask;

    public GrimHook(ReportSystemSpigot plugin) {
        this.plugin = plugin;

        // Grim async yuklenebilir — retry pattern
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!tryConnect()) {
                // Her saniye dene, max 30 deneme
                final int[] attempts = {0};
                retryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (connected || attempts[0] >= 30) {
                        if (retryTask != null) retryTask.cancel();
                        if (!connected) {
                            plugin.getLogger().warning("[GRIM] GrimAC API'ye 30 saniyede baglanamadi.");
                        }
                        return;
                    }
                    attempts[0]++;
                    tryConnect();
                }, 20L, 20L);
            }
        }, 20L);
    }

    private boolean tryConnect() {
        if (connected) return true;
        try {
            // Bukkit ServiceProvider ile API al
            RegisteredServiceProvider<GrimAbstractAPI> provider =
                    Bukkit.getServicesManager().getRegistration(GrimAbstractAPI.class);

            if (provider == null) return false;

            grimApi = provider.getProvider();
            if (grimApi == null || !grimApi.hasStarted()) return false;

            // Listener kaydet
            listener = new GrimDetectionListener(plugin, grimApi);
            listener.register();
            connected = true;

            if (retryTask != null) {
                retryTask.cancel();
                retryTask = null;
            }

            plugin.getLogger().info("[GRIM] GrimAC entegrasyonu aktif! (v" + grimApi.getGrimVersion() + ")");
            return true;

        } catch (Exception e) {
            plugin.debug("[GRIM] Baglanti denemesi: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
        if (listener != null) {
            listener.unregister();
            listener = null;
        }
        grimApi = null;
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }
}
