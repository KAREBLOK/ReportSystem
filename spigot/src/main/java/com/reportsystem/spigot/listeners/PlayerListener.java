package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final ReportSystemSpigot plugin;

    public PlayerListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Aktif replay'leri temizle
        plugin.getReplayManager().handlePlayerQuit(player);

        // Aktif kayıtları kontrol et (genelde handlePlayerQuit zaten durdurmuştur; kayıt
        // haritadan atomik alındığı için bildirim yalnız bir kez gider) — F-Ö3: çıkışta bildirim gönder
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) {
            plugin.getRecordingManager().stopRecording(player.getUniqueId(), true);
        }
    }

    // H-47: replay/Overwatch izleyicisinin komut filtresi ReplayProtectionListener.onCommand'a
    // tasindi (izin listesi + Overwatch post-replay kapsami + ad alanli komut soyma).
}