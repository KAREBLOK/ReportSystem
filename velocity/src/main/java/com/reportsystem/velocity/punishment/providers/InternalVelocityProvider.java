package com.reportsystem.velocity.punishment.providers;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.punishment.VelocityPunishmentManager.PunishmentProvider;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Dahili ceza saglayicisi. Aktif ban/mute'lar bellekte tutulur (kalici kaynak global_punishments tablosu).
 * Cevrimici cezalandirilan oyuncu UUID ile, cevrimdisi olan isimle anahtarlanir; isimle tutulan girdi
 * oyuncu giris yapinca {@link #bind(String, UUID)} ile UUID'ye tasinir (isim degisikligi sonrasi da gecerli).
 * Deger: bitis zamani (ms), kalici = {@link Long#MAX_VALUE}. Tum haritalar thread-safe (async giris kontrolu).
 */
public class InternalVelocityProvider implements PunishmentProvider {

    /**
     * DB "aktif ceza yok" dediginde bellekten silmeden once girdinin en az bu kadar eski olmasi gerekir:
     * yeni verilen cezanin async DB kaydi henuz yazilmamis olabilir.
     */
    private static final long FORGET_GRACE_MS = 30_000L;

    /** Girdi; isim, oyuncu cevrimdisiyken isimle kaldirma (unban/unmute) icin saklanir. createdAt 0 = DB'den yuklendi. */
    private record Entry(long end, String nameKey, long createdAt) {
        boolean expired(long now) {
            return end != Long.MAX_VALUE && end <= now;
        }
    }

    private final ReportSystemVelocity plugin;
    private final Map<UUID, Entry> mutedByUuid = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> bannedByUuid = new ConcurrentHashMap<>();
    private final Map<String, Entry> mutedByName = new ConcurrentHashMap<>();
    private final Map<String, Entry> bannedByName = new ConcurrentHashMap<>();

    public InternalVelocityProvider(ReportSystemVelocity plugin) {
        this.plugin = plugin;

        // Suresi dolmus ban/mute'lari temizleme task'i (her dakika)
        plugin.getServer().getScheduler().buildTask(plugin, this::cleanupExpiredMutes)
                .repeat(1L, TimeUnit.MINUTES)
                .schedule();
    }

    private static String key(String playerName) {
        return playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
    }

    private static long endOf(long duration) {
        return duration > 0 ? DurationFormat.expiryFrom(System.currentTimeMillis(), duration) : Long.MAX_VALUE;
    }

    /** Ayni anahtarda daha gec biten girdi kalir. */
    private static Entry later(Entry a, Entry b) {
        return a.end() >= b.end() ? a : b;
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        // Kalici kayit VelocityPunishmentManager.savePunishment ile veritabanina yazilir; burada giris kontrolu icin bellek
        put(bannedByUuid, bannedByName, playerName, endOf(duration));
        return true;
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long duration) {
        put(mutedByUuid, mutedByName, playerName, endOf(duration));
        return true;
    }

    private void put(Map<UUID, Entry> byUuid, Map<String, Entry> byName, String playerName, long end) {
        long now = System.currentTimeMillis();
        Player player = plugin.getServer().getPlayer(playerName).orElse(null);
        if (player != null) {
            byUuid.put(player.getUniqueId(), new Entry(end, key(player.getUsername()), now));
        } else {
            byName.put(key(playerName), new Entry(end, key(playerName), now));
        }
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        return plugin.getServer().getPlayer(playerName).map(player -> {
            String msg = plugin.getConfig().getMessage("kick.player-message",
                    "&cSunucudan atildiniz!\n\n&7Sebep: &f%reason%\n&7Atan yetkili: &f%staff%\n\n&aTekrar girebilirsiniz.",
                    "%reason%", reason, "%staff%", punisher);
            player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
            return true;
        }).orElse(false);
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        // Uyari bellekte tutulmaz; bildirim VelocityPunishmentManager.globalWarn'da
        return true;
    }

    @Override
    public boolean unban(String playerName) {
        remove(bannedByUuid, bannedByName, playerName);
        return true;
    }

    @Override
    public boolean unmute(String playerName) {
        remove(mutedByUuid, mutedByName, playerName);
        return true;
    }

    private void remove(Map<UUID, Entry> byUuid, Map<String, Entry> byName, String playerName) {
        String k = key(playerName);
        byName.remove(k);
        plugin.getServer().getPlayer(playerName).ifPresent(p -> byUuid.remove(p.getUniqueId()));
        // Cevrimdisi oyuncu: UUID girdisini kayitli isimden bul
        byUuid.values().removeIf(e -> e.nameKey().equals(k));
    }

    /**
     * DB "aktif ceza yok" dediginde bellekteki girdiyi siler (coklu proxy: obur proxy'nin unban/unmute'u
     * burada da gecerli olur). Son {@value #FORGET_GRACE_MS} ms icinde eklenen girdiye dokunulmaz
     * (bu proxy'de az once verilmis, DB kaydi henuz yazilmamis ceza).
     *
     * @param ban true = ban haritalari, false = mute haritalari
     * @return silinmeyip kalan (yeni, suresi dolmamis) bir girdi varsa true -> ceza hala gecerli sayilmali
     */
    public boolean forget(boolean ban, String playerName, UUID uuid) {
        Map<UUID, Entry> byUuid = ban ? bannedByUuid : mutedByUuid;
        Map<String, Entry> byName = ban ? bannedByName : mutedByName;
        long now = System.currentTimeMillis();
        long cutoff = now - FORGET_GRACE_MS;
        Entry keptByUuid = null;
        if (uuid != null) {
            keptByUuid = byUuid.computeIfPresent(uuid, (u, e) -> e.createdAt() <= cutoff || e.expired(now) ? null : e);
        }
        Entry keptByName = byName.computeIfPresent(key(playerName),
                (k, e) -> e.createdAt() <= cutoff || e.expired(now) ? null : e);
        return keptByUuid != null || keptByName != null;
    }

    @Override
    public boolean isBanned(String playerName) {
        UUID uuid = plugin.getServer().getPlayer(playerName).map(Player::getUniqueId).orElse(null);
        return check(bannedByUuid, bannedByName, playerName, uuid);
    }

    /** Giris kontrolu: oyuncu henuz proxy'ye kayitli degilken UUID dogrudan verilir (LoginEvent). */
    public boolean isBanned(String playerName, UUID uuid) {
        return check(bannedByUuid, bannedByName, playerName, uuid);
    }

    @Override
    public boolean isMuted(String playerName) {
        UUID uuid = plugin.getServer().getPlayer(playerName).map(Player::getUniqueId).orElse(null);
        return check(mutedByUuid, mutedByName, playerName, uuid);
    }

    private boolean check(Map<UUID, Entry> byUuid, Map<String, Entry> byName, String playerName, UUID uuid) {
        long now = System.currentTimeMillis();
        if (uuid != null) {
            Entry e = byUuid.get(uuid);
            if (e != null) {
                if (!e.expired(now)) {
                    return true;
                }
                byUuid.remove(uuid, e);
            }
        }
        String k = key(playerName);
        Entry e = byName.get(k);
        if (e == null) {
            return false;
        }
        if (!e.expired(now)) {
            return true;
        }
        byName.remove(k, e);
        return false;
    }

    /**
     * Acilista veritabanindaki aktif ban/mute'lari bellege yukler. ASYNC thread'den cagrilmali.
     */
    public void loadActive(Connection c) throws SQLException {
        String sql = "SELECT player_name, player_uuid, punishment_type, end_time FROM global_punishments "
                + "WHERE active = TRUE AND punishment_type IN ('ban','mute') AND (end_time IS NULL OR end_time > ?)";
        int loaded = 0;
        // Yuklenen girdiler "eski" sayilir: DB ile celisirse hemen silinebilir
        long created = 0L;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("player_name");
                    String uuidStr = rs.getString("player_uuid");
                    String type = rs.getString("punishment_type");
                    long end = rs.getLong("end_time");
                    if (rs.wasNull() || end <= 0) {
                        end = Long.MAX_VALUE;
                    }
                    boolean ban = "ban".equalsIgnoreCase(type);
                    Entry entry = new Entry(end, key(name), created);
                    UUID uuid = parseUuid(uuidStr);
                    if (uuid != null) {
                        (ban ? bannedByUuid : mutedByUuid).merge(uuid, entry, InternalVelocityProvider::later);
                    } else {
                        (ban ? bannedByName : mutedByName).merge(key(name), entry, InternalVelocityProvider::later);
                    }
                    loaded++;
                }
            }
        }
        plugin.getLogger().info("[PUNISHMENT] {} aktif ban/mute bellege yuklendi.", loaded);
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Giris yapan oyuncunun isimle tutulan cezalarini UUID'sine tasir (isim degisse de ceza takip eder).
     */
    public void bind(String name, UUID uuid) {
        if (name == null || uuid == null) {
            return;
        }
        String k = key(name);
        Entry ban = bannedByName.remove(k);
        if (ban != null) {
            bannedByUuid.merge(uuid, ban, InternalVelocityProvider::later);
        }
        Entry mute = mutedByName.remove(k);
        if (mute != null) {
            mutedByUuid.merge(uuid, mute, InternalVelocityProvider::later);
        }
    }

    /**
     * Suresi dolmus ban/mute'lari (UUID ve isim haritalarinda) temizler
     */
    private void cleanupExpiredMutes() {
        long now = System.currentTimeMillis();
        mutedByUuid.values().removeIf(e -> e.expired(now));
        bannedByUuid.values().removeIf(e -> e.expired(now));
        mutedByName.values().removeIf(e -> e.expired(now));
        bannedByName.values().removeIf(e -> e.expired(now));
    }
}
