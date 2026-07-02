package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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

        // Aktif kayıtları kontrol et
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) {
            plugin.getRecordingManager().stopRecording(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Replay izlerken belirli komutları engelle
        if (plugin.getReplayManager().isWatchingReplay(player)) {
            if (command.startsWith("/tp") || command.startsWith("/teleport") ||
                    command.startsWith("/warp") || command.startsWith("/home") ||
                    command.startsWith("/spawn") || command.startsWith("/tpa")) {

                event.setCancelled(true);
                plugin.getMessageManager().sendMessage(player, "replay.command-blocked");
                plugin.getMessageManager().sendMessage(player, "replay.command-blocked-tip");
            }
        }
    }
}