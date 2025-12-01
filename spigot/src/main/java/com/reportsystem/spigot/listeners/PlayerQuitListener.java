package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final ReportSystemSpigot plugin;

    public PlayerQuitListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // ReplayManager'ı plugin'den al
        plugin.getReplayManager().handlePlayerQuit(event.getPlayer());
    }
}