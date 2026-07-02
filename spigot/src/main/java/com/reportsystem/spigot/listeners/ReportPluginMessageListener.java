package com.reportsystem.spigot.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.punishment.providers.BungeeCordPunishmentProvider;
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

            // Proxy (Velocity/Bungee) bu 3 subchannel ile cevap gönderir:
            // PunishmentResult → ban/mute/kick/warn sonucu
            // UnpunishmentResult → unban/unmute sonucu
            // PunishmentResponse → legacy isim (eski akış için korundu)
            case "PunishmentResponse":
            case "PunishmentResult":
            case "UnpunishmentResult":
                handlePunishmentResponse(in);
                break;

            case "CheckPunishmentResult":
                handleCheckPunishmentResponse(in);
                break;

            default:
                plugin.getLogger().warning("Unknown subchannel: " + subChannel);
        }
    }

    private void handleReportCreated(ByteArrayDataInput in) {
        String reporterUUID = in.readUTF();
        int reportId = in.readInt();

        java.util.UUID uuid = parseUUID(reporterUUID);
        if (uuid == null) return;

        // Recording için report ID'yi güncelle
        plugin.updateRecordingReportId(uuid, reportId);

        plugin.debug("[DEBUG] Report created response received: #" + reportId);
    }

    private void handleStartReplay(ByteArrayDataInput in) {
        String playerUUID = in.readUTF();
        int reportId = in.readInt();

        java.util.UUID uuid = parseUUID(playerUUID);
        if (uuid == null) return;

        // Replay başlatma isteği
        Player targetPlayer = plugin.getServer().getPlayer(uuid);
        if (targetPlayer != null) {
            plugin.getReplayManager().startReplay(reportId, targetPlayer);
        } else {
            // Oyuncu bu sunucuda değil, bekleyen replay olarak kaydet
            plugin.storePendingReplay(uuid, reportId);
        }
    }

    private void handleUpdateRecording(ByteArrayDataInput in) {
        String playerUUID = in.readUTF();
        int reportId = in.readInt();

        java.util.UUID uuid = parseUUID(playerUUID);
        if (uuid == null) return;

        // Recording ID güncelle
        plugin.updateRecordingReportId(uuid, reportId);
    }

    private void handlePunishmentResponse(ByteArrayDataInput in) {
        String requestId = in.readUTF();
        boolean result = in.readBoolean();

        // Hangi provider aktifse ona yönlendir
        Object provider = plugin.getPunishmentManager().getProvider();
        if (provider instanceof GlobalPunishmentProvider) {
            ((GlobalPunishmentProvider) provider).handleResponse(requestId, result);
        } else if (provider instanceof BungeeCordPunishmentProvider) {
            ((BungeeCordPunishmentProvider) provider).handlePunishmentResult(requestId, result);
        }
    }

    private void handleCheckPunishmentResponse(ByteArrayDataInput in) {
        String requestId = in.readUTF();
        boolean result = in.readBoolean();

        Object provider = plugin.getPunishmentManager().getProvider();
        if (provider instanceof GlobalPunishmentProvider) {
            // Global provider check request için de aynı pendingRequests map'ini kullanıyor
            ((GlobalPunishmentProvider) provider).handleResponse(requestId, result);
        } else if (provider instanceof BungeeCordPunishmentProvider) {
            ((BungeeCordPunishmentProvider) provider).handleCheckResult(requestId, result);
        }
    }

    private java.util.UUID parseUUID(String raw) {
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[PLUGIN-MESSAGE] Geçersiz UUID formatı: " + raw);
            return null;
        }
    }
}