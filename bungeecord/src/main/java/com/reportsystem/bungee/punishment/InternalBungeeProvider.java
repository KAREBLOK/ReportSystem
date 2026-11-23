package com.reportsystem.bungee.punishment.providers;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.bungee.ReportSystemBungee;
import com.reportsystem.bungee.punishment.BungeePunishmentManager.PunishmentProvider;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

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
 * Dahili ceza sağlayıcısı. Aktif ban/mute'lar bellekte tutulur; ESAS kaynak global_punishments tablosudur,
 * bellek yalnız DB'ye erişilemediğinde yedek olarak kullanılır (bkz. BungeePunishmentManager).
 * Çevrimiçi cezalandırılan oyuncu UUID ile, çevrimdışı olan isimle anahtarlanır; isimle tutulan girdi
 * oyuncu giriş yapınca {@link #bind(String, UUID)} ile UUID'ye taşınır (isim değişikliği sonrası da geçerli).
 * Bitiş: ms, kalıcı = {@link Long#MAX_VALUE}. Tüm haritalar thread-safe (async giriş kontrolü).
 */
public class InternalBungeeProvider implements PunishmentProvider {

    /**
     * DB "aktif ceza yok" dediğinde bellekten silmeden önce girdinin en az bu kadar eski olması gerekir:
     * yeni verilen cezanın async DB kaydı henüz yazılmamış olabilir.
     */
    private static final long FORGET_GRACE_MS = 30_000L;

    /** Girdi; isim, oyuncu çevrimdışıyken isimle kaldırma (unban/unmute) için saklanır. */
    private record Entry(long end, String nameKey, long createdAt) {
        boolean expired(long now) {
            return end != Long.MAX_VALUE && end <= now;
        }
    }

    private final ReportSystemBungee plugin;
    private final Map<UUID, Entry> mutedByUuid = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> bannedByUuid = new ConcurrentHashMap<>();
    private final Map<String, Entry> mutedByName = new ConcurrentHashMap<>();
    private final Map<String, Entry> bannedByName = new ConcurrentHashMap<>();

    public InternalBungeeProvider(ReportSystemBungee plugin) {
        this.plugin = plugin;

        // Süresi dolmuş ban/mute'ları temizleme task'ı (her dakika)
        ProxyServer.getInstance().getScheduler().schedule(plugin, this::cleanupExpiredMutes, 0L, 1L, TimeUnit.MINUTES);
    }

    private static String key(String playerName) {
        return playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
    }

    private static long endOf(long duration) {
        return duration > 0 ? DurationFormat.expiryFrom(System.currentTimeMillis(), duration) : Long.MAX_VALUE;
    }

    /** Aynı anahtarda daha geç biten girdi kalır. */
    private static Entry later(Entry a, Entry b) {
        return a.end() >= b.end() ? a : b;
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long duration) {
        // Kalıcı kayıt BungeePunishmentManager.savePunishment ile veritabanına yazılır; burada yedek bellek
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
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        if (player != null) {
            byUuid.put(player.getUniqueId(), new Entry(end, key(player.getName()), now));
        } else {
            byName.put(key(playerName), new Entry(end, key(playerName), now));
        }
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        if (player != null) {
            String kickMsg = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.kick.player-message",
                            "&cSunucudan atıldınız!\n\n&7Sebep: &f%reason%\n&7Atan yetkili: &f%staff%\n\n&aTekrar girebilirsiniz."))
                    .replace("%reason%", reason)
                    .replace("%staff%", punisher);
            player.disconnect(kickMsg);
            return true;
        }
        return false;
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        // Uyarı bellekte tutulmaz; bildirim BungeePunishmentManager.globalWarn'da
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
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        if (player != null) {
            byUuid.remove(player.getUniqueId());
        }
        // Çevrimdışı oyuncu: UUID girdisini kayıtlı isimden bul
        byUuid.values().removeIf(e -> e.nameKey().equals(k));
    }

    @Override
    public boolean isBanned(String playerName) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        return check(bannedByUuid, bannedByName, playerName, player != null ? player.getUniqueId() : null);
    }

    /** Giriş kontrolü: oyuncu henüz ProxiedPlayer değilken UUID doğrudan verilir (LoginEvent). */
    public boolean isBanned(String playerName, UUID uuid) {
        return check(bannedByUuid, bannedByName, playerName, uuid);
    }

    @Override
    public boolean isMuted(String playerName) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        return check(mutedByUuid, mutedByName, playerName, player != null ? player.getUniqueId() : null);
    }

    /** UUID'si bilinen oyuncu için mute kontrolü (bellek yedeği). */
    public boolean isMuted(String playerName, UUID uuid) {
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
     * DB "aktif ceza yok" dediğinde bellekteki girdiyi siler (çoklu proxy: öbür proxy'nin unban/unmute'u
     * burada da geçerli olur). Son {@value #FORGET_GRACE_MS} ms içinde eklenen girdiye dokunulmaz
     * (bu proxy'de az önce verilmiş, DB kaydı henüz yazılmamış ceza).
     *
     * @param ban true = ban haritaları, false = mute haritaları
     * @return silinmeyip kalan (yeni, süresi dolmamış) bir girdi varsa true → ceza hâlâ geçerli sayılmalı
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

    /**
     * Açılışta veritabanındaki aktif ban/mute'ları belleğe yükler. ASYNC thread'den çağrılmalı.
     */
    public void loadActive(Connection c) throws SQLException {
        String sql = "SELECT player_name, player_uuid, punishment_type, end_time FROM global_punishments "
                + "WHERE active = TRUE AND punishment_type IN ('ban','mute') AND (end_time IS NULL OR end_time > ?)";
        int loaded = 0;
        // Yüklenen girdiler "eski" sayılır: DB ile çelişirse hemen silinebilir
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
                        (ban ? bannedByUuid : mutedByUuid).merge(uuid, entry, InternalBungeeProvider::later);
                    } else {
                        (ban ? bannedByName : mutedByName).merge(key(name), entry, InternalBungeeProvider::later);
                    }
                    loaded++;
                }
            }
        }
        plugin.getLogger().info("[PUNISHMENT] " + loaded + " aktif ban/mute belleğe yüklendi.");
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
     * Giriş yapan oyuncunun isimle tutulan cezalarını UUID'sine taşır (isim değişse de ceza takip eder).
     */
    public void bind(String name, UUID uuid) {
        if (name == null || uuid == null) {
            return;
        }
        String k = key(name);
        Entry ban = bannedByName.remove(k);
        if (ban != null) {
            bannedByUuid.merge(uuid, ban, InternalBungeeProvider::later);
        }
        Entry mute = mutedByName.remove(k);
        if (mute != null) {
            mutedByUuid.merge(uuid, mute, InternalBungeeProvider::later);
        }
    }

    /**
     * Süresi dolmuş ban/mute'ları (UUID ve isim haritalarında) temizler
     */
    private void cleanupExpiredMutes() {
        long now = System.currentTimeMillis();
        mutedByUuid.values().removeIf(e -> e.expired(now));
        bannedByUuid.values().removeIf(e -> e.expired(now));
        mutedByName.values().removeIf(e -> e.expired(now));
        bannedByName.values().removeIf(e -> e.expired(now));
    }
}
