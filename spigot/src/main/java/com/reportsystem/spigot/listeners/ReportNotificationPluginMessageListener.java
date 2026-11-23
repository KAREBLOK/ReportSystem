package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BungeeCord'dan gelen rapor bildirimlerini dinler
 */
public class ReportNotificationPluginMessageListener implements PluginMessageListener {

    private static final long SIG_WARN_INTERVAL_MS = 60_000L;

    private final ReportSystemSpigot plugin;
    /** Imza uyarisi hiz siniri: anahtar (tasiyici oyuncu) -> son uyari zamani. */
    private final Map<String, Long> lastSigWarn = new ConcurrentHashMap<>();

    public ReportNotificationPluginMessageListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("reportsystem:notification")) {
            return;
        }

        // HMAC-SHA256 dogrulamasi (bkz. MessageSigner): sahte yetkili bildirimlerini engeller
        if (plugin.getMessageSigner() == null) {
            return;
        }
        byte[] payload = plugin.getMessageSigner().verify(channel, message);
        if (payload == null) {
            String carrier = player != null ? player.getUniqueId().toString() : "?";
            if (shouldWarn(channel + ":" + carrier)) {
                plugin.getLogger().warning("[PLUGIN-MESSAGE] Gecersiz imzali bildirim reddedildi (tasiyici: "
                        + (player != null ? player.getName() : "?") + "). (60 sn icinde tekrarlar loglanmaz)");
            }
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            String subChannel = in.readUTF();

            if (subChannel.equals("NewReport")) {
                int reportId = in.readInt();
                String reporterName = in.readUTF();
                String reportedName = in.readUTF();
                String reason = in.readUTF();
                String serverName = in.readUTF();

                // Yetkililere bildirim gönder
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getMessageManager().sendStaffNotification(reportId, reporterName, reportedName, reason, serverName);
                });
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Plugin mesajı okunamadı: " + e.getMessage());
        }
    }

    /**
     * Imza uyarilarini anahtar basina 60 sn'de bire sinirlar (log taskini; bkz. proxy dinleyicileri).
     */
    private boolean shouldWarn(String key) {
        long now = System.currentTimeMillis();
        boolean[] warn = {false};
        lastSigWarn.compute(key, (k, prev) -> {
            if (prev == null || now - prev >= SIG_WARN_INTERVAL_MS) {
                warn[0] = true;
                return now;
            }
            return prev;
        });
        if (warn[0] && lastSigWarn.size() > 256) {
            lastSigWarn.values().removeIf(t -> now - t >= SIG_WARN_INTERVAL_MS);
        }
        return warn[0];
    }
}
