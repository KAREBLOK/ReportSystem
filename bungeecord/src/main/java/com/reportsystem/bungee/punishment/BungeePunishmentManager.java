package com.reportsystem.bungee.punishment;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.bungee.punishment.providers.LiteBansBungeeProvider;
import com.reportsystem.bungee.punishment.providers.AdvancedBanBungeeProvider;
import com.reportsystem.bungee.punishment.providers.InternalBungeeProvider;
import com.reportsystem.bungee.ReportSystemBungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

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
            InternalBungeeProvider internal = new InternalBungeeProvider(plugin);
            this.provider = internal;
            plugin.getLogger().info("Dahili ceza sistemi kullanılıyor.");

            // Aktif ban/mute'ları açılışta belleğe yükle (async — proxy başlangıcını bekletmez)
            ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
                try (Connection c = plugin.getDatabase().getConnection()) {
                    internal.loadActive(c);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Aktif cezalar yüklenemedi!", e);
                }
            });
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

        // UUID disconnect'ten ÖNCE alınır (atıldıktan sonra getPlayer null döner → kayıt UUID'siz kalırdı)
        ProxiedPlayer target = ProxyServer.getInstance().getPlayer(playerName);
        UUID targetUuid = target != null ? target.getUniqueId() : null;

        boolean success = provider.ban(playerName, reason, punisher, duration);

        if (success) {
            // Oyuncu online ise at
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
            savePunishment(playerName, targetUuid, "ban", reason, punisher, duration, serverOrigin);

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

        ProxiedPlayer target = ProxyServer.getInstance().getPlayer(playerName);
        UUID targetUuid = target != null ? target.getUniqueId() : null;

        boolean success = provider.mute(playerName, reason, punisher, duration);

        if (success) {
            // Oyuncuya bildir
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
            savePunishment(playerName, targetUuid, "mute", reason, punisher, duration, serverOrigin);

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
     * Ceza durumunu kontrol eder. ASYNC thread'den çağrılmalı (DB sorgusu yapar).
     * DB erişilebilirse DB esastır (DB "yok" derse bellek girdisi de silinir — çoklu proxy'de öbür proxy'nin
     * unban/unmute'u geçerli olsun); DB hatasında bellekteki sonuca düşülür.
     */
    public boolean isActivePunishment(String playerName, String type) {
        if (!"ban".equals(type) && !"mute".equals(type)) {
            return false;
        }
        boolean ban = "ban".equals(type);
        ProxiedPlayer online = ProxyServer.getInstance().getPlayer(playerName);
        UUID uuid = online != null ? online.getUniqueId() : null;

        boolean memory;
        if (provider instanceof InternalBungeeProvider internal) {
            memory = ban ? internal.isBanned(playerName, uuid) : internal.isMuted(playerName, uuid);
        } else {
            memory = ban ? provider.isBanned(playerName) : provider.isMuted(playerName);
        }

        if (plugin.getDatabase() == null) {
            return memory;
        }
        // UUID biliniyorsa: UUID'li kayıt ya da UUID'si henüz bağlanmamış isim kaydı; bilinmiyorsa isimle
        String sql = uuid != null
                ? "SELECT 1 FROM global_punishments WHERE punishment_type = ? AND active = TRUE " +
                  "AND (end_time IS NULL OR end_time > ?) " +
                  "AND (player_uuid = ? OR ((player_uuid IS NULL OR player_uuid = '') AND player_name = ?)) LIMIT 1"
                : "SELECT 1 FROM global_punishments WHERE punishment_type = ? AND active = TRUE " +
                  "AND (end_time IS NULL OR end_time > ?) AND player_name = ? LIMIT 1";
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(3);
            stmt.setString(1, type);
            stmt.setLong(2, System.currentTimeMillis());
            if (uuid != null) {
                stmt.setString(3, uuid.toString());
                stmt.setString(4, playerName);
            } else {
                stmt.setString(3, playerName);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return true;
                }
            }
            // DB'de yok: bellekteki eski girdi silinir; yalnız bu proxy'de az önce verilmiş (DB'ye henüz
            // yazılmamış) ceza kalırsa geçerli sayılır
            if (memory && provider instanceof InternalBungeeProvider internal) {
                return internal.forget(ban, playerName, uuid);
            }
            return false;
        } catch (SQLException e) {
            plugin.getLogger().warning("Ceza kontrolü DB hatası (" + playerName + ", " + type
                    + "), bellek sonucu kullanılıyor: " + e.getMessage());
            return memory;
        }
    }

    /**
     * Giriş ban kontrolü. LoginEvent kimlik doğrulamasından SONRA gelir → UUID kesin (giriş öncesi
     * olayda online-mode'da UUID henüz yoktur). Olay Netty thread'inde çağrılır; DB işi intent + runAsync ile yapılır.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        if (event.isCancelled()) {
            return;
        }
        event.registerIntent(plugin);
        try {
            ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
                try {
                    checkLoginBan(event);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Giriş ban kontrolü hatası ("
                            + event.getConnection().getName() + ")", t);
                } finally {
                    event.completeIntent(plugin);
                }
            });
        } catch (Throwable t) {
            // Görev planlanamadı: girişi asılı bırakma
            plugin.getLogger().log(Level.SEVERE, "Giriş ban kontrolü planlanamadı!", t);
            event.completeIntent(plugin);
        }
    }

    /**
     * ASYNC giriş ban kontrolü. DB ESAS, bellek YEDEK:
     * <ul>
     *   <li>DB satır döndürürse → ayrıntılı mesajla (sebep, kalan süre) red.</li>
     *   <li>DB "aktif ban yok" derse → bellekteki eski girdi silinir, giriş serbest (çoklu proxy'de öbür
     *       proxy'nin unban'ı işlesin). Yalnız bu proxy'de az önce verilmiş, DB'ye henüz yazılmamış ban kalırsa red.</li>
     *   <li>DB hatası → bellek banlı diyorsa genel mesajla red, değilse fail-open (eski davranış).</li>
     * </ul>
     * Ardından isimle verilmiş (UUID'siz) aktif cezalar bu UUID'ye bağlanır.
     */
    private void checkLoginBan(LoginEvent event) {
        UUID uuid = event.getConnection().getUniqueId();
        String name = event.getConnection().getName();

        // Bellek (yedek) sonucu — dahili sağlayıcıda UUID de kontrol edilir
        boolean memoryBanned = provider instanceof InternalBungeeProvider internal
                ? internal.isBanned(name, uuid)
                : provider.isBanned(name);

        try {
            try (Connection conn = plugin.getDatabase().getConnection()) {
                // (1) Tek sorgu: UUID ile, ya da UUID'si henüz bilinmeyen (çevrimdışı verilmiş) isim kaydı
                boolean dbBanned = false;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id, reason, end_time, player_uuid FROM global_punishments " +
                                "WHERE punishment_type = 'ban' AND active = TRUE " +
                                "AND (end_time IS NULL OR end_time > ?) " +
                                "AND (player_uuid = ? OR ((player_uuid IS NULL OR player_uuid = '') AND player_name = ?)) " +
                                "ORDER BY start_time DESC LIMIT 1")) {
                    stmt.setQueryTimeout(3);
                    long now = System.currentTimeMillis();
                    stmt.setLong(1, now);
                    if (uuid != null) {
                        stmt.setString(2, uuid.toString());
                    } else {
                        stmt.setNull(2, Types.VARCHAR);
                    }
                    stmt.setString(3, name);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            dbBanned = true;
                            String reason = rs.getString("reason");
                            if (reason == null || reason.isEmpty()) {
                                reason = plugin.getConfig().getString("messages.punishment.unknown-reason", "Bilinmeyen sebep");
                            }
                            long endTime = rs.getLong("end_time");
                            boolean permanent = rs.wasNull() || endTime <= 0;

                            String message;
                            if (!permanent) {
                                message = getMessage("ban.login-blocked-temp",
                                                "&cSunucudan yasaklandınız!\n\n&7Sebep: &f%reason%\n&7Kalan süre: &f%duration%")
                                        .replace("%reason%", reason)
                                        .replace("%duration%", formatDuration(endTime - now));
                            } else {
                                message = getMessage("ban.login-blocked-permanent",
                                                "&cSunucudan kalıcı olarak yasaklandınız!\n\n&7Sebep: &f%reason%\n\n&8Ban kaldırma başvurusu için: discord.gg/sunucu")
                                        .replace("%reason%", reason);
                            }
                            event.setCancelled(true);
                            event.setCancelReason(TextComponent.fromLegacyText(message));
                        }
                    }
                }

                // (2) DB'de aktif ban yok: bellekteki eski girdiyi sil; az önce bu proxy'de verilmiş ban kalırsa red
                if (!dbBanned && memoryBanned) {
                    boolean stillBanned = provider instanceof InternalBungeeProvider internal
                            && internal.forget(true, name, uuid);
                    if (stillBanned) {
                        rejectGeneric(event);
                    }
                }

                // (3) UUID bağlama: isimle verilmiş aktif cezalar bu UUID'ye bağlanır (isim değişse de takip eder).
                // Banlı girişte de yapılır — asıl amacı budur.
                if (uuid != null) {
                    try (PreparedStatement bind = conn.prepareStatement(
                            "UPDATE global_punishments SET player_uuid = ? " +
                                    "WHERE (player_uuid IS NULL OR player_uuid = '') AND player_name = ? AND active = TRUE")) {
                        bind.setQueryTimeout(3);
                        bind.setString(1, uuid.toString());
                        bind.setString(2, name);
                        bind.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            // DB'ye erişilemedi: bellek yedeğine düş; bellek de temizse fail-open (eski davranış)
            plugin.getLogger().severe("Giriş ban kontrolü DB hatası (" + name + "): " + e.getMessage()
                    + (memoryBanned ? " — bellek kaydına göre reddedildi" : " — giriş engellenmedi"));
            if (memoryBanned && !event.isCancelled()) {
                rejectGeneric(event);
            }
        } finally {
            if (uuid != null && provider instanceof InternalBungeeProvider internal) {
                internal.bind(name, uuid);
            }
        }
    }

    /** Ayrıntı bilinmeyen (yalnız bellek kaynaklı) ban reddi. */
    private void rejectGeneric(LoginEvent event) {
        event.setCancelled(true);
        event.setCancelReason(TextComponent.fromLegacyText(getMessage("ban.login-blocked-provider",
                "&cBu sunucudan yasaklandınız!")));
    }

    /**
     * Cezayı veritabanına kaydeder
     */
    private void savePunishment(String playerName, UUID targetUuid, String type, String reason, String punisher,
                                long duration, String serverOrigin) {
        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO global_punishments (player_name, player_uuid, punishment_type, " +
                                 "reason, punisher, start_time, end_time, active, server_origin) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                 )) {

                // UUID çağıran tarafından (disconnect'ten önce) alınır; offline oyuncuda NULL yazılır
                // (boş string sonradan WHERE player_uuid = ? sorgularını bozar). Giriş anında bağlanır.
                String playerUUID = targetUuid != null ? targetUuid.toString() : null;

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
                    stmt.setLong(7, DurationFormat.expiryFrom(System.currentTimeMillis(), duration));
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
     * Ceza durumunu günceller (async). Oyuncu çevrimiçiyse UUID'si, değilse DB'de isimle bilinen son UUID
     * kullanılır; böylece UUID'ye bağlanmış (isim değiştirmiş) kayıtlar da kapanır.
     */
    private void updatePunishmentStatus(String playerName, String type, boolean active) {
        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection()) {
                String playerUUID = null;
                ProxiedPlayer online = ProxyServer.getInstance().getPlayer(playerName);
                if (online != null) {
                    playerUUID = online.getUniqueId().toString();
                } else {
                    try (PreparedStatement find = conn.prepareStatement(
                            "SELECT player_uuid FROM global_punishments WHERE player_name = ? " +
                                    "AND player_uuid IS NOT NULL AND player_uuid <> '' ORDER BY start_time DESC LIMIT 1")) {
                        find.setString(1, playerName);
                        try (ResultSet rs = find.executeQuery()) {
                            if (rs.next()) {
                                playerUUID = rs.getString("player_uuid");
                            }
                        }
                    }
                }

                String sql = playerUUID != null
                        ? "UPDATE global_punishments SET active = ? WHERE punishment_type = ? AND active = ? " +
                          "AND (player_name = ? OR player_uuid = ?)"
                        : "UPDATE global_punishments SET active = ? WHERE punishment_type = ? AND active = ? " +
                          "AND player_name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setBoolean(1, active);
                    stmt.setString(2, type);
                    stmt.setBoolean(3, !active);
                    stmt.setString(4, playerName);
                    if (playerUUID != null) {
                        stmt.setString(5, playerUUID);
                    }
                    int changed = stmt.executeUpdate();
                    if (changed == 0) {
                        plugin.getLogger().warning("[PUNISHMENT] " + type + " durumu güncellenemedi: " + playerName
                                + " için " + (active ? "pasif" : "aktif") + " kayıt bulunamadı (DB'de 0 satır).");
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Ceza durumu güncelleme hatası: " + e.getMessage());
            }
        });
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