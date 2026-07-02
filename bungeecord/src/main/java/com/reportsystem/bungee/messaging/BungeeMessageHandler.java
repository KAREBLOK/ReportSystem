package com.reportsystem.bungee.messaging;

import com.reportsystem.bungee.ReportSystemBungee;
import com.reportsystem.common.messaging.PluginMessageHandler;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.IOException;
import java.util.Collection;

public class BungeeMessageHandler {

    private final ReportSystemBungee plugin;

    public BungeeMessageHandler(ReportSystemBungee plugin) {
        this.plugin = plugin;
    }

    /**
     * Tüm sunuculara mesaj gönderir
     */
    public void sendToAllServers(PluginMessageHandler.MessageType type, Object... data) {
        try {
            byte[] message = PluginMessageHandler.createMessage(type, data);

            for (ServerInfo server : ProxyServer.getInstance().getServers().values()) {
                Collection<ProxiedPlayer> players = server.getPlayers();
                if (!players.isEmpty()) {
                    ProxiedPlayer player = players.iterator().next();
                    player.getServer().sendData(PluginMessageHandler.CHANNEL, message);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Tüm sunuculara mesaj gönderilirken hata: " + e.getMessage());
        }
    }

    /**
     * Belirli bir sunucuya mesaj gönderir
     */
    public void sendToServer(String serverName, PluginMessageHandler.MessageType type, Object... data) {
        try {
            byte[] message = PluginMessageHandler.createMessage(type, data);

            ServerInfo server = ProxyServer.getInstance().getServerInfo(serverName);
            if (server != null) {
                Collection<ProxiedPlayer> players = server.getPlayers();
                if (!players.isEmpty()) {
                    ProxiedPlayer player = players.iterator().next();
                    player.getServer().sendData(PluginMessageHandler.CHANNEL, message);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Sunucuya mesaj gönderilirken hata (" + serverName + "): " + e.getMessage());
        }
    }

    /**
     * Rapor oluşturulduğunda tüm sunuculara bildirim gönderir
     */
    public void notifyReportCreated(int reportId, String reporter, String reported, String reason, String server) {
        sendToAllServers(PluginMessageHandler.MessageType.REPORT_CREATED,
                reportId, reporter, reported, reason, server);
    }

    /**
     * Broadcast mesajı gönderir
     */
    public void broadcast(String message, String permission) {
        sendToAllServers(PluginMessageHandler.MessageType.BROADCAST_MESSAGE, message, permission);
    }
}