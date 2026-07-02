package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.punishment.providers.InternalPunishmentProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class PunishmentListener implements Listener {

    private final ReportSystemSpigot plugin;

    public PunishmentListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Eğer internal provider kullanılıyorsa, mute kontrolü zaten orada yapılıyor
        // Bu listener sadece ek kontroller için

        if (plugin.getPunishmentManager().getProvider().isMuted(player.getName())) {
            // Mute mesajı InternalPunishmentProvider tarafından gösterilecek
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Bekleyen uyarıları kontrol et
        if (!plugin.getConfigManager().isUseGlobalPunishments()) {
            if (plugin.getPunishmentManager().getProvider() instanceof InternalPunishmentProvider) {
                InternalPunishmentProvider provider = (InternalPunishmentProvider) plugin.getPunishmentManager().getProvider();

                int warnings = provider.getWarningCount(player.getName());
                if (warnings > 0) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        player.sendMessage("");
                        plugin.getMessageManager().sendMessage(player, "punishments.warn.login-warning",
                                "%count%", String.valueOf(warnings));
                        plugin.getMessageManager().sendMessage(player, "punishments.warn.login-warning-detail");
                        player.sendMessage("");
                    }, 40L); // 2 saniye sonra göster
                }
            }
        }
    }
}