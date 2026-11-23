package com.reportsystem.velocity.punishment;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.config.VelocityConfig;
import com.reportsystem.velocity.punishment.providers.AdvancedBanVelocityProvider;
import com.reportsystem.velocity.punishment.providers.InternalVelocityProvider;
import com.reportsystem.velocity.punishment.providers.LiteBansVelocityProvider;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.sql.*;
import java.util.UUID;

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
            InternalVelocityProvider internal = new InternalVelocityProvider(plugin);
            this.provider = internal;
            plugin.getLogger().info("Dahili ceza sistemi kullaniliyor.");

            // Aktif ban/mute'lari acilista bellege yukle (async — proxy baslangicini bekletmez)
            plugin.getServer().getScheduler().buildTask(plugin, () -> {
                try (Connection c = plugin.getDatabase().getConnection()) {
                    internal.loadActive(c);
                } catch (SQLException e) {
                    plugin.getLogger().error("Aktif cezalar yuklenemedi!", e);
                }
            }).schedule();
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

        // UUID disconnect'ten ONCE alinir (atildiktan sonra getPlayer bos doner -> kayit UUID'siz kalirdi)
        UUID targetUuid = plugin.getServer().getPlayer(playerName).map(Player::getUniqueId).orElse(null);

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
            savePunishment(playerName, targetUuid, "ban", reason, punisher, duration, serverOrigin);

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

        UUID targetUuid = plugin.getServer().getPlayer(playerName).map(Player::getUniqueId).orElse(null);

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

            savePunishment(playerName, targetUuid, "mute", reason, punisher, duration, serverOrigin);
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
     * Ceza durumunu kontrol eder (CheckPunishment). Dahili saglayicida DB esastir, bellek yedektir:
     * DB erisilebilirse sonucu DB belirler (aktif kayit yoksa bellekteki eski girdi silinir — baska proxy'de
     * kaldirilmis ceza); DB hata verirse bellek sonucuna dusulur. Harici saglayici (LiteBans/AdvancedBan)
     * kendi kaynagini kullanir. ASYNC thread'den cagrilmali (DB).
     */
    public boolean isActivePunishment(String playerName, String type) {
        if (!"ban".equals(type) && !"mute".equals(type)) {
            return false;
        }
        boolean ban = "ban".equals(type);
        if (!(provider instanceof InternalVelocityProvider internal)) {
            return ban ? provider.isBanned(playerName) : provider.isMuted(playerName);
        }

        UUID uuid = plugin.getServer().getPlayer(playerName).map(Player::getUniqueId).orElse(null);
        try (Connection conn = plugin.getDatabase().getConnection()) {
            if (findActive(conn, type, playerName, uuid, System.currentTimeMillis()) != null) {
                return true;
            }
            // DB'de yok: bellekteki eski girdi silinir; yalniz bu proxy'de az once verilmis (DB'ye henuz
            // yazilmamis) ceza kalirsa gecerli sayilir
            return internal.forget(ban, playerName, uuid);
        } catch (SQLException e) {
            plugin.getLogger().warn("Ceza kontrolu DB hatasi ({} / {}), bellek sonucu kullaniliyor: {}",
                    playerName, type, e.getMessage());
            return ban ? internal.isBanned(playerName) : internal.isMuted(playerName);
        }
    }

    /** DB'deki aktif ceza satiri (yalniz giris mesaji icin gereken alanlar). endTime null = kalici. */
    private record ActiveRow(String reason, Long endTime) {
    }

    /**
     * Aktif ceza satirini bulur; yoksa null. UUID biliniyorsa UUID'ye bagli kayit ya da UUID'si henuz
     * bilinmeyen (NULL / bos) isim kaydi; UUID bilinmiyorsa isimle eslesen her kayit.
     */
    private ActiveRow findActive(Connection conn, String type, String name, UUID uuid, long now) throws SQLException {
        String sql = "SELECT reason, end_time FROM global_punishments " +
                "WHERE punishment_type = ? AND active = TRUE AND (end_time IS NULL OR end_time > ?) " +
                (uuid != null
                        ? "AND (player_uuid = ? OR ((player_uuid IS NULL OR player_uuid = '') AND player_name = ?)) "
                        : "AND player_name = ? ") +
                "ORDER BY start_time DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(3);
            stmt.setString(1, type);
            stmt.setLong(2, now);
            if (uuid != null) {
                stmt.setString(3, uuid.toString());
                stmt.setString(4, name);
            } else {
                stmt.setString(3, name);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String reason = rs.getString("reason");
                long end = rs.getLong("end_time");
                Long endTime = (rs.wasNull() || end <= 0) ? null : end;
                return new ActiveRow(reason, endTime);
            }
        }
    }

    /**
     * Giris ban kontrolu. LoginEvent kimlik dogrulamasindan SONRA gelir -> UUID kesin (giris oncesi
     * olayda online-mode'da UUID henuz yoktur). DB isi EventTask.async ile Velocity'nin async havuzunda yapilir;
     * olay bu gorev bitene kadar bekletilir. LATE: diger eklentilerin kararindan sonra calisir.
     * (Subscribe.order 3.3'te deprecated; priority yalniz PostOrder.CUSTOM ile gecerli oldugundan uyari bastirildi.)
     */
    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.LATE)
    public EventTask onLogin(LoginEvent event) {
        return EventTask.async(() -> {
            if (!event.getResult().isAllowed()) {
                return; // Baska bir eklenti girisi zaten reddetti
            }
            try {
                checkLoginBan(event);
            } catch (Throwable t) {
                plugin.getLogger().error("Giris ban kontrolu hatasi ({})", event.getPlayer().getUsername(), t);
            }
        });
    }

    /**
     * ASYNC giris kontrolu. DB esas, bellek yedek:
     * DB aktif ban satiri dondururse ayrintili mesajla (sebep, kalan sure) reddedilir; DB "aktif ban yok" derse
     * dahili bellekteki girdi silinir ve giris serbest (baska proxy'de unban); DB hata verirse bellek/saglayici
     * sonucuna dusulur (banli ise genel mesajla red, degilse fail-open). Ardindan isimle tutulan cezalara UUID baglanir.
     */
    private void checkLoginBan(LoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getUsername();
        InternalVelocityProvider internal = provider instanceof InternalVelocityProvider i ? i : null;

        // (1) Bellek / harici saglayici sonucu (dahili saglayicida UUID de kontrol edilir)
        boolean memBanned = false;
        try {
            memBanned = internal != null ? internal.isBanned(name, uuid) : provider.isBanned(name);
        } catch (RuntimeException e) {
            plugin.getLogger().warn("Saglayici ban kontrolu basarisiz ({}): {}", name, e.getMessage());
        }

        // (2) DB: tek sorgu + UUID baglama
        long now = System.currentTimeMillis();
        ActiveRow row = null;
        boolean dbOk = false;
        try (Connection conn = plugin.getDatabase().getConnection()) {
            row = findActive(conn, "ban", name, uuid, now);
            dbOk = true;

            // UUID baglama: isimle verilmis aktif cezalar bu UUID'ye baglanir (isim degisse de takip eder).
            // Banli giriste de yapilir — asil amaci budur.
            if (uuid != null) {
                try (PreparedStatement bind = conn.prepareStatement(
                        "UPDATE global_punishments SET player_uuid = ? " +
                                "WHERE (player_uuid IS NULL OR player_uuid = '') AND player_name = ? AND active = TRUE")) {
                    bind.setQueryTimeout(3);
                    bind.setString(1, uuid.toString());
                    bind.setString(2, name);
                    bind.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warn("UUID baglama hatasi ({}): {}", name, e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Giris ban kontrolu DB hatasi ({}), bellek sonucu kullaniliyor: {}",
                    name, e.getMessage());
        }

        // (3) Bellekteki isim girdisini UUID'ye tasi (asagidaki temizlik iki haritayi da kapsar)
        if (uuid != null && internal != null) {
            internal.bind(name, uuid);
        }

        // (4) Karar
        if (row != null) {
            String reason = row.reason();
            if (reason == null || reason.isEmpty()) {
                reason = config().getMessage("unknown-reason", "Bilinmeyen sebep");
            }
            String msg;
            if (row.endTime() != null) {
                msg = config().getMessage("ban.login-temp",
                        "&cSunucudan yasaklandiniz!\n\n&7Sebep: &f%reason%\n&7Kalan sure: &f%duration%",
                        "%reason%", reason, "%duration%", formatDuration(row.endTime() - now));
            } else {
                msg = config().getMessage("ban.login-permanent",
                        "&cSunucudan kalici olarak yasaklandiniz!\n\n&7Sebep: &f%reason%\n\n&8Ban kaldirma basvurusu icin: discord.gg/sunucu",
                        "%reason%", reason);
            }
            event.setResult(ResultedEvent.ComponentResult.denied(colorize(msg)));
            return;
        }

        if (dbOk && internal != null) {
            // DB esas: aktif ban yok -> bellekteki eski girdi (baska proxy'de kaldirilmis) silinir, giris serbest.
            // Istisna: bu proxy'de son 30 sn icinde verilmis (DB kaydi henuz yazilmamis olabilecek) ban -> red.
            if (internal.forget(true, name, uuid)) {
                String msg = config().getMessage("ban.login-denied", "&cBu sunucudan yasaklandiniz!");
                event.setResult(ResultedEvent.ComponentResult.denied(colorize(msg)));
            } else if (memBanned) {
                plugin.getLogger().info("[PUNISHMENT] {} icin bellekteki ban DB'de aktif degil, bellekten silindi.", name);
            }
            return;
        }

        // DB hatasi (bellek yedek) ya da harici saglayicinin kendi bani
        if (memBanned) {
            String msg = config().getMessage("ban.login-denied", "&cBu sunucudan yasaklandiniz!");
            event.setResult(ResultedEvent.ComponentResult.denied(colorize(msg)));
        }
        // Aksi halde fail-open (eski davranis)
    }

    /**
     * Cezayi veritabanina kaydeder
     */
    private void savePunishment(String playerName, UUID targetUuid, String type, String reason, String punisher,
                                long duration, String serverOrigin) {
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO global_punishments (player_name, player_uuid, punishment_type, " +
                                 "reason, punisher, start_time, end_time, active, server_origin) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                 )) {

                // Offline oyuncular için UUID bilinmiyor — boş string yerine NULL yaz
                // (boş string sonradan WHERE player_uuid = ? sorgularını bozar).
                // UUID cagiran tarafta (disconnect'ten once) alinmistir; burada yeniden aranmaz.
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
                plugin.getLogger().error("Ceza kaydetme hatasi: {}", e.getMessage());
            }
        }).schedule();
    }

    /**
     * Ceza durumunu gunceller (async). Oyuncu cevrimiciyse UUID'si, degilse DB'de isimle bilinen son UUID
     * kullanilir; boylece UUID'ye baglanmis (isim degistirmis) kayitlar da kapanir.
     */
    private void updatePunishmentStatus(String playerName, String type, boolean active) {
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection()) {
                String playerUUID = plugin.getServer().getPlayer(playerName)
                        .map(p -> p.getUniqueId().toString())
                        .orElse(null);
                if (playerUUID == null) {
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
                        plugin.getLogger().warn("[PUNISHMENT] {} durumu guncellenemedi: {} icin aktif kayit bulunamadi (0 satir).",
                                type, playerName);
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().error("Ceza durumu guncelleme hatasi: {}", e.getMessage());
            }
        }).schedule();
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
