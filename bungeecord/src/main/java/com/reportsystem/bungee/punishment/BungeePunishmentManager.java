package com.reportsystem.bungee.punishment;

import com.reportsystem.bungee.punishment.providers.LiteBansBungeeProvider;
import com.reportsystem.bungee.punishment.providers.AdvancedBanBungeeProvider;
import com.reportsystem.bungee.punishment.providers.InternalBungeeProvider;
import com.reportsystem.bungee.ReportSystemBungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BungeePunishmentManager implements Listener {

    private final ReportSystemBungee plugin;
    private PunishmentProvider provider;

    public BungeePunishmentManager(ReportSystemBungee plugin) {
        this.plugin = plugin;

        // BungeeCord'da eklenti kontrolü
        if (ProxyServer.getInstance().getPluginManager().getPlugin("LiteBans") != null) {
            this.provider = new LiteBansBungeeProvider();
            plugin.getLogger().info("LiteBans (BungeeCord) entegrasyonu aktif!");
        } else if (ProxyServer.getInstance().getPluginManager().getPlugin("AdvancedBan") != null) {
            this.provider = new AdvancedBanBungeeProvider();
            plugin.getLogger().info("AdvancedBan (BungeeCord) entegrasyonu aktif!");
        } else {
            this.provider = new InternalBungeeProvider(plugin);
            plugin.getLogger().info("Dahili ceza sistemi kullanılıyor.");
        }
    }

    /**
     * Config'den mesaj alır ve renk kodlarını çevirir
     */
    private String getMessage(String path, String defaultValue) {
        String msg = plugin.getConfig().getString("messages." + path, defaultValue);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * Global ban uygular
     */
    public boolean globalBan(String playerName, String reason, String punisher, long duration, String serverOrigin) {
        plugin.getLogger().info("[PUNISHMENT] Global ban for " + playerName + " from " + serverOrigin);

        boolean success = provider.ban(playerName, reason, punisher, duration);

        if (success) {
            // Oyuncu online ise at
            ProxiedPlayer target = ProxyServer.getInstance().getPlayer(playerName);
            if (target != null) {
                String kickMessage;
                if (duration > 0) {
                    kickMessage = getMessage("ban.kick-temp",
                                    "&cSunucudan yasaklandınız!\n\n&7Sebep: &f%reason%\n&7Süre: &f%duration%\n&7Cezayı veren: &f%staff%")
                            .replace("%reason%", reason)
                            .replace("%duration%", formatDuration(duration))
                            .replace("%staff%", punisher);
                } else {
                    kickMessage = getMessage("ban.kick-permanent",
                                    "&cSunucudan kalıcı olarak yasaklandınız!\n\n&7Sebep: &f%reason%\n&7Cezayı veren: &f%staff%")
                            .replace("%reason%", reason)
                            .replace("%staff%", punisher);
                }
                target.disconnect(kickMessage);
            }

            // Veritabanına kaydet
            savePunishment(playerName, "ban", reason, punisher, duration, serverOrigin);

            // Tüm sunuculara bildir
            notifyAllServers(playerName, reason, punisher, "ban", duration);
        }

        return success;
    }

    /**
     * Global mute uygular
     */
    public boolean globalMute(String playerName, String reason, String punisher, long duration, String serverOrigin) {
        plugin.getLogger().info("[PUNISHMENT] Global mute for " + playerName + " from " + serverOrigin);

        boolean success = provider.mute(playerName, reason, punisher, duration);

        if (success) {
            // Oyuncuya bildir
            ProxiedPlayer target = ProxyServer.getInstance().getPlayer(playerName);
            if (target != null) {
                String durationText = duration > 0 ? formatDuration(duration) :
                        getMessage("duration.permanent", "Kalıcı");
                String muteMsg = getMessage("mute.player-message",
                                "&c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n&c          SUSTURULDUNUZ!\n\n&7  Sebep: &f%reason%\n&7  Süre: &f%duration%\n&7  Yetkili: &f%staff%\n&c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                        .replace("%reason%", reason)
                        .replace("%duration%", durationText)
                        .replace("%staff%", punisher);

                for (String line : muteMsg.split("\\\\n|\\n")) {
                    target.sendMessage(line);
                }
            }

            // Veritabanına kaydet
            savePunishment(playerName, "mute", reason, punisher, duration, serverOrigin);

            // Tüm sunuculara bildir
            notifyAllServers(playerName, reason, punisher, "mute", duration);
        }

        return success;
    }

    /**
     * Global kick uygular
     */
    public boolean globalKick(String playerName, String reason, String punisher, String serverOrigin) {
        plugin.getLogger().info("[PUNISHMENT] Global kick for " + playerName + " from " + serverOrigin);

        ProxiedPlayer target = ProxyServer.getInstance().getPlayer(playerName);
        if (target != null) {
            String kickMessage = getMessage("kick.player-message",
                            "&cSunucudan atıldınız!\n\n&7Sebep: &f%reason%\n&7Atan yetkili: &f%staff%\n\n&aTekrar girebilirsiniz.")
                    .replace("%reason%", reason)
                    .replace("%staff%", punisher);

            target.disconnect(kickMessage);

            // Tüm sunuculara bildir
            notifyAllServers(playerName, reason, punisher, "kick", 0);

            return true;
        }

        return false;
    }

    /**
     * Global warn uygular
     */
    public boolean globalWarn(String playerName, String reason, String punisher, String serverOrigin) {
        plugin.getLogger().info("[PUNISHMENT] Global warn for " + playerName + " from " + serverOrigin);

        boolean success = provider.warn(playerName, reason, punisher);

        if (success) {
            ProxiedPlayer target = ProxyServer.getInstance().getPlayer(playerName);
            if (target != null) {
                String warnMsg = getMessage("warn.player-message",
                                "&6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n&6          ⚠ UYARI ⚠\n\n&c  Bir yetkili tarafından uyarıldınız!\n\n&7  Sebep: &f%reason%\n&7  Yetkili: &f%staff%\n\n&c  Kuralları ihlal etmeye devam ederseniz\n&c  daha ağır cezalar alabilirsiniz!\n&6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                        .replace("%reason%", reason)
                        .replace("%staff%", punisher);

                for (String line : warnMsg.split("\\\\n|\\n")) {
                    target.sendMessage(line);
                }
            }

            // Tüm sunuculara bildir
            notifyAllServers(playerName, reason, punisher, "warn", 0);
        }

        return success;
    }

    /**
     * Ceza kaldırır
     */
    public boolean removePunishment(String playerName, String type) {
        plugin.getLogger().info("[PUNISHMENT] Removing " + type + " for " + playerName);

        boolean success = false;

        switch (type) {
            case "ban":
                success = provider.unban(playerName);
                if (success) {
                    updatePunishmentStatus(playerName, "ban", false);
                }
                break;
            case "mute":
                success = provider.unmute(playerName);
                if (success) {
                    updatePunishmentStatus(playerName, "mute", false);
                }
                break;
        }

        return success;
    }

    /**
     * Ceza durumunu kontrol eder
     */
    public boolean isActivePunishment(String playerName, String type) {
        switch (type) {
            case "ban":
                return provider.isBanned(playerName);
            case "mute":
                return provider.isMuted(playerName);
            default:
                return false;
        }
    }

    /**
     * Giriş öncesi ban kontrolü
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(PreLoginEvent event) {
        String playerName = event.getConnection().getName();

        // Önce provider üzerinden kontrol et
        if (provider.isBanned(playerName)) {
            event.setCancelled(true);
            event.setCancelReason(getMessage("ban.login-blocked-provider",
                    "&cBu sunucudan yasaklandınız!"));
            return;
        }

        // Veritabanından da kontrol et (backup)
        if (isActivePunishmentInDB(playerName, "ban")) {
            event.setCancelled(true);

            String reason = getPunishmentReason(playerName, "ban");
            long remainingTime = getRemainingTime(playerName, "ban");

            String message;
            if (remainingTime > 0) {
                message = getMessage("ban.login-blocked-temp",
                                "&cSunucudan yasaklandınız!\n\n&7Sebep: &f%reason%\n&7Kalan süre: &f%duration%")
                        .replace("%reason%", reason)
                        .replace("%duration%", formatDuration(remainingTime));
            } else {
                message = getMessage("ban.login-blocked-permanent",
                                "&cSunucudan kalıcı olarak yasaklandınız!\n\n&7Sebep: &f%reason%\n\n&8Ban kaldırma başvurusu için: discord.gg/sunucu")
                        .replace("%reason%", reason);
            }

            event.setCancelReason(message);
        }
    }

    /**
     * Cezayı veritabanına kaydeder
     */
    private void savePunishment(String playerName, String type, String reason, String punisher,
                                long duration, String serverOrigin) {
        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO global_punishments (player_name, player_uuid, punishment_type, " +
                                 "reason, punisher, start_time, end_time, active, server_origin) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                 )) {

                // Offline oyuncular için UUID bilinmiyor — boş string yerine NULL yaz
                // (boş string sonradan WHERE player_uuid = ? sorgularını bozar)
                String playerUUID = null;
                ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
                if (player != null) {
                    playerUUID = player.getUniqueId().toString();
                }

                stmt.setString(1, playerName);
                if (playerUUID != null) {
                    stmt.setString(2, playerUUID);
                } else {
                    stmt.setNull(2, Types.VARCHAR);
                }
                stmt.setString(3, type);
                stmt.setString(4, reason);
                stmt.setString(5, punisher);
                stmt.setLong(6, System.currentTimeMillis());

                if (duration > 0) {
                    stmt.setLong(7, System.currentTimeMillis() + duration);
                } else {
                    stmt.setNull(7, Types.BIGINT);
                }

                stmt.setBoolean(8, true);
                stmt.setString(9, serverOrigin);

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Ceza kaydetme hatası: " + e.getMessage());
            }
        });
    }

    /**
     * Ceza durumunu günceller
     */
    private void updatePunishmentStatus(String playerName, String type, boolean active) {
        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE global_punishments SET active = ? WHERE player_name = ? AND punishment_type = ? AND active = ?"
                 )) {

                stmt.setBoolean(1, active);
                stmt.setString(2, playerName);
                stmt.setString(3, type);
                stmt.setBoolean(4, !active);

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Ceza durumu güncelleme hatası: " + e.getMessage());
            }
        });
    }

    /**
     * Veritabanından aktif ceza kontrolü
     */
    private boolean isActivePunishmentInDB(String playerName, String type) {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM global_punishments WHERE player_name = ? AND punishment_type = ? " +
                             "AND active = TRUE AND (end_time IS NULL OR end_time > ?)"
             )) {

            stmt.setString(1, playerName);
            stmt.setString(2, type);
            stmt.setLong(3, System.currentTimeMillis());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Ceza kontrol hatası: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ceza sebebini alır
     */
    private String getPunishmentReason(String playerName, String type) {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT reason FROM global_punishments WHERE player_name = ? AND punishment_type = ? " +
                             "AND active = TRUE ORDER BY start_time DESC LIMIT 1"
             )) {

            stmt.setString(1, playerName);
            stmt.setString(2, type);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("reason");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Ceza sebebi alma hatası: " + e.getMessage());
        }

        return plugin.getConfig().getString("messages.punishment.unknown-reason", "Bilinmeyen sebep");
    }

    /**
     * Kalan ceza süresini alır
     */
    private long getRemainingTime(String playerName, String type) {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT end_time FROM global_punishments WHERE player_name = ? AND punishment_type = ? " +
                             "AND active = TRUE ORDER BY start_time DESC LIMIT 1"
             )) {

            stmt.setString(1, playerName);
            stmt.setString(2, type);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long endTime = rs.getLong("end_time");
                    if (endTime > 0) {
                        return endTime - System.currentTimeMillis();
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Ceza süresi alma hatası: " + e.getMessage());
        }

        return -1; // Kalıcı
    }

    /**
     * Tüm sunuculara ceza bildirimini gönderir
     */
    private void notifyAllServers(String playerName, String reason, String punisher, String type, long duration) {
        String message = "";

        switch (type) {
            case "ban":
                if (duration > 0) {
                    message = getMessage("notify.ban-temp",
                            "&c[CEZA] &f%player% &csunucudan %duration% süreliğine yasaklandı!")
                            .replace("%player%", playerName)
                            .replace("%duration%", formatDuration(duration));
                } else {
                    message = getMessage("notify.ban-permanent",
                            "&c[CEZA] &f%player% &csunucudan kalıcı olarak yasaklandı!")
                            .replace("%player%", playerName);
                }
                break;
            case "mute":
                if (duration > 0) {
                    message = getMessage("notify.mute-temp",
                            "&6[CEZA] &f%player% &6%duration% süreliğine susturuldu!")
                            .replace("%player%", playerName)
                            .replace("%duration%", formatDuration(duration));
                } else {
                    message = getMessage("notify.mute-permanent",
                            "&6[CEZA] &f%player% &6kalıcı olarak susturuldu!")
                            .replace("%player%", playerName);
                }
                break;
            case "kick":
                message = getMessage("notify.kick",
                        "&e[CEZA] &f%player% &esunucudan atıldı!")
                        .replace("%player%", playerName);
                break;
            case "warn":
                message = getMessage("notify.warn",
                        "&e[CEZA] &f%player% &euyarı aldı!")
                        .replace("%player%", playerName);
                break;
        }

        String reasonLine = getMessage("notify.reason-line", "&7Sebep: &f%reason%")
                .replace("%reason%", reason);
        String staffLine = getMessage("notify.staff-line", "&7Yetkili: &f%staff%")
                .replace("%staff%", punisher);

        message += "\n" + reasonLine;
        message += "\n" + staffLine;

        // Tüm yetkililere bildir
        for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
            if (player.hasPermission("reportsystem.notify")) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Süreyi formatlar
     */
    String formatDuration(long millis) {
        String daysWord = plugin.getConfig().getString("messages.duration.days", "gün");
        String hoursWord = plugin.getConfig().getString("messages.duration.hours", "saat");
        String minutesWord = plugin.getConfig().getString("messages.duration.minutes", "dakika");
        String secondsWord = plugin.getConfig().getString("messages.duration.seconds", "saniye");

        if (millis <= 0) return "0 " + secondsWord;

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " " + daysWord + (hours % 24 > 0 ? " " + (hours % 24) + " " + hoursWord : "");
        }
        if (hours > 0) {
            return hours + " " + hoursWord + (minutes % 60 > 0 ? " " + (minutes % 60) + " " + minutesWord : "");
        }
        if (minutes > 0) {
            return minutes + " " + minutesWord + (seconds % 60 > 0 ? " " + (seconds % 60) + " " + secondsWord : "");
        }
        return seconds + " " + secondsWord;
    }

    // Interface tanımı
    public interface PunishmentProvider {
        boolean ban(String playerName, String reason, String punisher, long duration);
        boolean mute(String playerName, String reason, String punisher, long duration);
        boolean kick(String playerName, String reason, String punisher);
        boolean warn(String playerName, String reason, String punisher);
        boolean unban(String playerName);
        boolean unmute(String playerName);
        boolean isBanned(String playerName);
        boolean isMuted(String playerName);
    }
}