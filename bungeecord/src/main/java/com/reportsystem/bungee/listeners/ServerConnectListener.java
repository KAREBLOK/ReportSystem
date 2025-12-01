package com.reportsystem.bungee.listeners;

import com.reportsystem.bungee.messaging.BungeeMessageHandler;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class ServerConnectListener implements Listener {

    private final BungeeMessageHandler messageHandler;

    public ServerConnectListener(BungeeMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        // Oyuncu sunucuya bağlandığında
        String playerName = event.getPlayer().getName();
        String serverName = event.getServer().getInfo().getName();

        // Diğer sunuculara bildir (opsiyonel)
        // messageHandler.notifyPlayerJoined(playerName, serverName);
    }
}