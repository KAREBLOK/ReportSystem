package com.reportsystem.spigot.hooks.polar;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import top.polar.api.PolarApi;
import top.polar.api.PolarApiAccessor;
import top.polar.api.exception.PolarNotLoadedException;

import java.lang.ref.WeakReference;

/**
 * Polar Anti-Cheat API hook.
 * Callback + retry pattern: once callback dener, basarisiz olursa her saniye tekrar dener.
 */
public class PolarHook {

    private final ReportSystemSpigot plugin;
    private PolarApi polarApi;
    private PolarDetectionListener detectionListener;
    private boolean connected = false;
    private BukkitTask retryTask;

    public PolarHook(ReportSystemSpigot plugin) {
        this.plugin = plugin;

        // 1. Callback kaydet
        try {
            top.polar.api.loader.LoaderApi.registerEnableCallback(this::tryConnect);
        } catch (Exception e) {
            plugin.debug("[POLAR] LoaderApi callback hatasi: " + e.getMessage());
        }

        // 2. Belki Polar zaten yuklu — hemen dene, olmazsa retry task baslat
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!connected) {
                tryConnect();
            }
            // Hala baglanamadiysa her 20 tick dene (max 30 deneme = 30 saniye)
            if (!connected) {
                final int[] attempts = {0};
                retryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (connected || attempts[0] >= 30) {
                        if (retryTask != null) retryTask.cancel();
                        if (!connected) {
                            plugin.getLogger().warning("[POLAR] Polar API'ye 30 saniyede baglanamadi.");
                        }
                        return;
                    }
                    attempts[0]++;
                    tryConnect();
                }, 20L, 20L);
            }
        }, 20L);
    }

    private void tryConnect() {
        if (connected) return;
        try {
            WeakReference<PolarApi> ref = PolarApiAccessor.access();
            polarApi = ref.get();

            if (polarApi == null) return;

            detectionListener = new PolarDetectionListener(plugin, polarApi);
            detectionListener.register();
            connected = true;

            if (retryTask != null) {
                retryTask.cancel();
                retryTask = null;
            }

            boolean cloud = polarApi.server().cloudConnected();
            plugin.getLogger().info("[POLAR] Polar Anti-Cheat entegrasyonu aktif!" +
                    (cloud ? " (Cloud: Bagli)" : " (Cloud: Bagli degil)"));

        } catch (PolarNotLoadedException e) {
            // Henuz yuklenemedi, retry devam edecek
        } catch (Exception e) {
            plugin.debug("[POLAR] Baglanti denemesi basarisiz: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
        if (detectionListener != null) {
            detectionListener.unregister();
            detectionListener = null;
        }
        polarApi = null;
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public PolarApi getApi() {
        return polarApi;
    }
}
