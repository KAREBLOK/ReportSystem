package com.reportsystem.spigot.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.punishment.providers.GlobalPunishmentProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class ReportPluginMessageListener implements PluginMessageListener {

    private final ReportSystemSpigot plugin;

    public ReportPluginMessageListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(plugin.getChannelName())) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();

        switch (subChannel) {
            case "ReportCreated":
                handleReportCreated(in);
                break;

            case "StartReplay":
                handleStartReplay(in);
                break;

            case "UpdateRecording":
                handleUpdateRecording(in);
                break;

            case "PunishmentResponse":
                handlePunishmentResponse(in);
                break;

            default:
                plugin.getLogger().warning("Unknown subchannel: " + subChannel);
        }
    }

    private void handleReportCreated(ByteArrayDataInput in) {
        String reporterUUID = in.readUTF();
        int reportId = in.readInt();

        // Recording için report ID'yi güncelle
        plugin.updateRecordingReportId(java.util.UUID.fromString(reporterUUID), reportId);

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Report created response received: #" + reportId);
        }
    }

    private void handleStartReplay(ByteArrayDataInput in) {
        String playerUUID = in.readUTF();
        int reportId = in.readInt();

        // Replay başlatma isteği
        Player targetPlayer = plugin.getServer().getPlayer(java.util.UUID.fromString(playerUUID));
        if (targetPlayer != null) {
            plugin.getReplayManager().startReplay(reportId, targetPlayer);
        } else {
            // Oyuncu bu sunucuda değil, bekleyen replay olarak kaydet
            plugin.storePendingReplay(java.util.UUID.fromString(playerUUID), reportId);
        }
    }

    private void handleUpdateRecording(ByteArrayDataInput in) {
        String playerUUID = in.readUTF();
        int reportId = in.readInt();

        // Recording ID güncelle
        plugin.updateRecordingReportId(java.util.UUID.fromString(playerUUID), reportId);
    }

    private void handlePunishmentResponse(ByteArrayDataInput in) {
        String requestId = in.readUTF();
        boolean result = in.readBoolean();

        // GlobalPunishmentProvider'a cevabı ilet
        if (plugin.getPunishmentManager().getProvider() instanceof GlobalPunishmentProvider) {
            GlobalPunishmentProvider provider = (GlobalPunishmentProvider) plugin.getPunishmentManager().getProvider();
            provider.handleResponse(requestId, result);
        }
    }
}