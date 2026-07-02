package com.reportsystem.velocity.punishment;

import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.config.VelocityConfig;
import com.reportsystem.velocity.punishment.providers.AdvancedBanVelocityProvider;
import com.reportsystem.velocity.punishment.providers.InternalVelocityProvider;
import com.reportsystem.velocity.punishment.providers.LiteBansVelocityProvider;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.sql.*;

public class VelocityPunishmentManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ReportSystemVelocity plugin;
    private final PunishmentProvider provider;

    public VelocityPunishmentManager(ReportSystemVelocity plugin) {
        this.plugin = plugin;

        // Velocity'de eklenti kontrolu
        if (plugin.getServer().getPluginManager().getPlugin("litebans").isPresent()) {
            this.provider = new LiteBansVelocityProvider(plugin);
            plugin.getLogger().info("LiteBans (Velocity) entegrasyonu aktif!");
        } else if (plugin.getServer().getPluginManager().getPlugin("advancedban").isPresent()) {
            this.provider = new AdvancedBanVelocityProvider(plugin);
            plugin.getLogger().info("AdvancedBan (Velocity) entegrasyonu aktif!");
        } else {
            this.provider = new InternalVelocityProvider(plugin);
            plugin.getLogger().info("Dahili ceza sistemi kullaniliyor.");
        }
    }

    private VelocityConfig config() {
        return plugin.getConfig();
    }

    /**
     * &-kodlu mesaji Component'e cevirir
     */
    private Component colorize(String text) {
        return LEGACY.deserialize(text);
    }

    /**
     * Global ban uygular
     */
    public boolean globalBan(String playerName, String reason, String punisher, long duration, String serverOrigin) {
        plugin.getLogger().info("[PUNISHMENT] Global ban for {} from {}", playerName, serverOrigin);

        boolean success = provider.ban(playerName, reason, punisher, duration);

        if (success) {
            // Oyuncu online ise at
            plugin.getServer().getPlayer(playerName).ifPresent(target -> {
                String msg;
                if (duration > 0) {
                    msg = config().getMessage("ban.kick-temp",
                            "&cSunucudan yasaklandiniz!\n\n&7Sebep: &f%reason%\n&7Sure: &f%duration%\n&7Yetkili: &f%staff%",
                            "%reason%", reason, "%duration%", formatDuration(duration), "%staff%", punisher);
                } else {
                    msg = config().getMessage("ban.kick-permanent",
                            "&cSunucudan kalici olarak yasaklandiniz!\n\n&7Sebep: &f%reason%\n&7Yetkili: &f%staff%",
                            "%reason%", reason, "%staff%", punisher);
                }
                target.disconnect(colorize(msg));
            });

            // Veritabanina kaydet
            savePunishment(playerName, "ban", reason, punisher, duration, serverOrigin);

            // Tum sunuculara bildir
            notifyAllServers(playerName, reason, punisher, "ban", duration);
        }

        return success;
    }

    /**
     * Global mute uygular
     */
    public boolean globalMute(String playerName, String reason, String punisher, long duration, String serverOrigin) {
        plugin.getLogger().info("[PUNISHMENT] Global mute for {} from {}", playerName, serverOrigin);

        boolean success = provider.mute(playerName, reason, punisher, duration);

        if (success) {
            plugin.getServer().getPlayer(playerName).ifPresent(target -> {
                String durationText = duration > 0 ? formatDuration(duration)
                        : config().getMessage("duration.permanent", "Kalici");
                String msg = config().getMessage("mute.player-message",
                        "&c\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\n&c          SUSTURULDUNUZ!\n\n&7  Sebep: &f%reason%\n&7  Sure: &f%duration%\n&7  Yetkili: &f%staff%\n&c\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC",
                        "%reason%", reason, "%duration%", durationText, "%staff%", punisher);

                // Satirlara ayirarak gonder (multiline mesaj)
                for (String line : msg.split("\\\\n|\\n")) {
                    target.sendMessage(colorize(line));
                }
            });

            savePunishment(playerName, "mute", reason, punisher, duration, serverOrigin);
            notifyAllServers(playerName, reason, punisher, "mute", duration);
        }

        return success;
    }

    /**
     * Global kick uygular
     */
    public boolean globalKick(String playerName, String reason, String punisher, String serverOrigin) {
        plugin.getLogger().info("[PUNISHMENT] Global kick for {} from {}", playerName, serverOrigin);

        Player target = plugin.getServer().getPlayer(playerName).orElse(null);
        if (target != null) {
            String msg = config().getMessage("kick.player-message",
                    "&cSunucudan atildiniz!\n\n&7Sebep: &f%reason%\n&7Atan yetkili: &f%staff%\n\n&aTekrar girebilirsiniz.",
                    "%reason%", reason, "%staff%", punisher);
            target.disconnect(colorize(msg));
            notifyAllServers(playerName, reason, punisher, "kick", 0);
            return true;
        }

        return false;
    }

    /**
     * Global warn uygular
     */
    public boolean globalWarn(String playerName, String reason, String punisher, String serverOrigin) {
        plugin.getLogger().info("[PUNISHMENT] Global warn for {} from {}", playerName, serverOrigin);

        boolean success = provider.warn(playerName, reason, punisher);

        if (success) {
            plugin.getServer().getPlayer(playerName).ifPresent(target -> {
                String msg = config().getMessage("warn.player-message",
                        "&6\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\n&6          \u26A0 UYARI \u26A0\n\n&c  Bir yetkili tarafindan uyarildiniz!\n\n&7  Sebep: &f%reason%\n&7  Yetkili: &f%staff%\n\n&c  Kurallari ihlal etmeye devam ederseniz\n&c  daha agir cezalar alabilirsiniz!\n&6\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC",
                        "%reason%", reason, "%staff%", punisher);

                // Satirlara ayirarak gonder
                for (String line : msg.split("\\\\n|\\n")) {
                    target.sendMessage(colorize(line));
                }
            });

            notifyAllServers(playerName, reason, punisher, "warn", 0);
        }

        return success;
    }

    /**
     * Ceza kaldirir
     */
    public boolean removePunishment(String playerName, String type) {
        plugin.getLogger().info("[PUNISHMENT] Removing {} for {}", type, playerName);

        return switch (type) {
            case "ban" -> {
                boolean s = provider.unban(playerName);
                if (s) updatePunishmentStatus(playerName, "ban", false);
                yield s;
            }
            case "mute" -> {
                boolean s = provider.unmute(playerName);
                if (s) updatePunishmentStatus(playerName, "mute", false);
                yield s;
            }
            default -> false;
        };
    }

    /**
     * Ceza durumunu kontrol eder
     */
    public boolean isActivePunishment(String playerName, String type) {
        return switch (type) {
            case "ban" -> provider.isBanned(playerName);
            case "mute" -> provider.isMuted(playerName);
            default -> false;
        };
    }

    /**
     * Giris oncesi ban kontrolu - Velocity'de async calisir (login thread'i bloklamaz)
     */
    @Subscribe
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            String playerName = event.getUsername();

            // Provider uzerinden kontrol et
            if (provider.isBanned(playerName)) {
                String msg = config().getMessage("ban.login-denied",
                        "&cBu sunucudan yasaklandiniz!");
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(colorize(msg)));
                return;
            }

            // Veritabanindan da kontrol et (backup)
            if (isActivePunishmentInDB(playerName, "ban")) {
                String reason = getPunishmentReason(playerName, "ban");
                long remainingTime = getRemainingTime(playerName, "ban");

                String msg;
                if (remainingTime > 0) {
                    msg = config().getMessage("ban.login-temp",
                            "&cSunucudan yasaklandiniz!\n\n&7Sebep: &f%reason%\n&7Kalan sure: &f%duration%",
                            "%reason%", reason, "%duration%", formatDuration(remainingTime));
                } else {
                    msg = config().getMessage("ban.login-permanent",
                            "&cSunucudan kalici olarak yasaklandiniz!\n\n&7Sebep: &f%reason%\n\n&8Ban kaldirma basvurusu icin: discord.gg/sunucu",
                            "%reason%", reason);
                }

                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(colorize(msg)));
            }
        });
    }

    /**
     * Cezayi veritabanina kaydeder
     */
    private void savePunishment(String playerName, String type, String reason, String punisher,
                                long duration, String serverOrigin) {
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO global_punishments (player_name, player_uuid, punishment_type, " +
                                 "reason, punisher, start_time, end_time, active, server_origin) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                 )) {

                // Offline oyuncular için UUID bilinmiyor — boş string yerine NULL yaz
                // (boş string sonradan WHERE player_uuid = ? sorgularını bozar)
                String playerUUID = plugin.getServer().getPlayer(playerName)
                        .map(p -> p.getUniqueId().toString())
                        .orElse(null);

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
                plugin.getLogger().error("Ceza kaydetme hatasi: {}", e.getMessage());
            }
        }).schedule();
    }

    /**
     * Ceza durumunu gunceller
     */
    private void updatePunishmentStatus(String playerName, String type, boolean active) {
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
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
                plugin.getLogger().error("Ceza durumu guncelleme hatasi: {}", e.getMessage());
            }
        }).schedule();
    }

    /**
     * Veritabanindan aktif ceza kontrolu
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
            plugin.getLogger().error("Ceza kontrol hatasi: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ceza sebebini alir
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
            plugin.getLogger().error("Ceza sebebi alma hatasi: {}", e.getMessage());
        }

        return config().getMessage("unknown-reason", "Bilinmeyen sebep");
    }

    /**
     * Kalan ceza suresini alir
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
            plugin.getLogger().error("Ceza suresi alma hatasi: {}", e.getMessage());
        }

        return -1; // Kalici
    }

    /**
     * Tum sunuculara ceza bildirimini gonderir
     */
    private void notifyAllServers(String playerName, String reason, String punisher, String type, long duration) {
        String notifyMsg = switch (type) {
            case "ban" -> {
                if (duration > 0) {
                    yield config().getMessage("notify.ban-temp",
                            "&c[CEZA] &f%player% &csunucudan %duration% sureligine yasaklandi!",
                            "%player%", playerName, "%duration%", formatDuration(duration));
                } else {
                    yield config().getMessage("notify.ban-permanent",
                            "&c[CEZA] &f%player% &csunucudan kalici olarak yasaklandi!",
                            "%player%", playerName);
                }
            }
            case "mute" -> {
                if (duration > 0) {
                    yield config().getMessage("notify.mute-temp",
                            "&6[CEZA] &f%player% &6%duration% sureligine susturuldu!",
                            "%player%", playerName, "%duration%", formatDuration(duration));
                } else {
                    yield config().getMessage("notify.mute-permanent",
                            "&6[CEZA] &f%player% &6kalici olarak susturuldu!",
                            "%player%", playerName);
                }
            }
            case "kick" -> config().getMessage("notify.kick",
                    "&e[CEZA] &f%player% &esunucudan atildi!",
                    "%player%", playerName);
            case "warn" -> config().getMessage("notify.warn",
                    "&e[CEZA] &f%player% &euyari aldi!",
                    "%player%", playerName);
            default -> "";
        };

        String reasonLine = config().getMessage("notify.reason", "&7Sebep: &f%reason%",
                "%reason%", reason);
        String staffLine = config().getMessage("notify.staff", "&7Yetkili: &f%staff%",
                "%staff%", punisher);

        Component fullMessage = colorize(notifyMsg)
                .append(Component.newline())
                .append(colorize(reasonLine))
                .append(Component.newline())
                .append(colorize(staffLine));

        for (Player player : plugin.getServer().getAllPlayers()) {
            if (player.hasPermission("reportsystem.notify")) {
                player.sendMessage(fullMessage);
            }
        }
    }

    /**
     * Sureyi formatlar (config'den okunan kelimelerle)
     */
    public String formatDuration(long millis) {
        if (millis <= 0) return config().getMessage("duration.zero", "0 saniye");

        String dayWord = config().getMessage("duration.days", "gun");
        String hourWord = config().getMessage("duration.hours", "saat");
        String minuteWord = config().getMessage("duration.minutes", "dakika");
        String secondWord = config().getMessage("duration.seconds", "saniye");

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " " + dayWord + (hours % 24 > 0 ? " " + (hours % 24) + " " + hourWord : "");
        }
        if (hours > 0) {
            return hours + " " + hourWord + (minutes % 60 > 0 ? " " + (minutes % 60) + " " + minuteWord : "");
        }
        if (minutes > 0) {
            return minutes + " " + minuteWord + (seconds % 60 > 0 ? " " + (seconds % 60) + " " + secondWord : "");
        }
        return seconds + " " + secondWord;
    }

    // Provider interface
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
