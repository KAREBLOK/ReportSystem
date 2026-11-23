package com.reportsystem.spigot.punishment.providers;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proxy (BungeeCord/Velocity) ile yapılan ceza isteklerini ANA THREAD'İ BEKLETMEDEN takip eder.
 *
 * Neden: proxy cevabı plugin message olarak ana thread'de işlenir; ana thread'de future.get() ile
 * beklemek cevabın işlenmesini engeller (deadlock + 5 sn donma). Bu sınıf bekleme yapmaz:
 * istek gönderilir, cevap gelince (veya zaman aşımında) bir Bukkit task'ı yetkiliyi bilgilendirir.
 *
 * isBanned/isMuted için cevap beklenmez; son bilinen değer (önbellek) döner ve arka planda
 * tazeleme sorgusu gönderilir. Ban/mute/unban/unmute istekleri başarılı olunca önbellek güncellenir.
 *
 * trackAction/trackCheck/send/abort/complete YALNIZ ANA THREAD'den çağrılmalıdır (Bukkit.getOnlinePlayers,
 * sendPluginMessage, scheduler). Async çağrı yollarını (örn. AsyncPlayerChatEvent -> isMuted) sağlayıcılar
 * Bukkit.isPrimaryThread() ile ayırır ve işi runTask ile ana thread'e atar. cached() her thread'den güvenlidir.
 */
final class ProxyRequestTracker {

    private static final String CHANNEL = "reportsystem:channel";
    private static final long TIMEOUT_TICKS = 100L; // 5 saniye

    private enum Kind { ACTION, CHECK }

    private record Pending(Kind kind, String type, String player, String staff, BukkitTask timeout) { }

    private final JavaPlugin plugin;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Set<String> inflightChecks = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    ProxyRequestTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Proxy'ye göndermek için çevrimiçi bir oyuncu gerekir (plugin message kısıtı). */
    boolean send(byte[] data) {
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            return false;
        }
        if (!(plugin instanceof ReportSystemSpigot rs) || rs.getMessageSigner() == null) {
            plugin.getLogger().warning("[PROXY] message-secret hazir degil, istek gonderilmedi.");
            return false;
        }
        // HMAC-SHA256 zarfi (bkz. MessageSigner): imzasiz mesaj proxy'de reddedilir
        rs.sendSigned(sender, CHANNEL, data);
        return true;
    }

    /** Eylem isteğini (ban/mute/kick/warn/unban/unmute) takibe alır; süre dolarsa yetkiliyi uyarır. */
    void trackAction(String requestId, String type, String player, String staff) {
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending p = pending.remove(requestId);
            if (p != null) {
                notifyStaff(p, "punishments.proxy.timeout");
            }
        }, TIMEOUT_TICKS);
        pending.put(requestId, new Pending(Kind.ACTION, type, player, staff, timeout));
    }

    /**
     * Tazeleme sorgusunu takibe alır. Aynı oyuncu/tür için zaten yoldaysa false döner (tekrar gönderme).
     */
    boolean trackCheck(String requestId, String type, String player) {
        String key = key(type, player);
        if (!inflightChecks.add(key)) {
            return false;
        }
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(requestId) != null) {
                inflightChecks.remove(key);
            }
        }, TIMEOUT_TICKS);
        pending.put(requestId, new Pending(Kind.CHECK, type, player, null, timeout));
        return true;
    }

    /** Gönderim başarısız olursa kaydı geri al. */
    void abort(String requestId) {
        Pending p = pending.remove(requestId);
        if (p != null) {
            p.timeout().cancel();
            if (p.kind() == Kind.CHECK) {
                inflightChecks.remove(key(p.type(), p.player()));
            }
        }
    }

    /** Proxy cevabı (ana thread). Bilinmeyen/süresi dolmuş istek sessizce yok sayılır. */
    void complete(String requestId, boolean result) {
        Pending p = pending.remove(requestId);
        if (p == null) {
            return;
        }
        p.timeout().cancel();

        if (p.kind() == Kind.CHECK) {
            inflightChecks.remove(key(p.type(), p.player()));
            cache.put(key(p.type(), p.player()), result);
            return;
        }

        if (result) {
            switch (p.type()) {
                case "ban", "mute" -> cache.put(key(p.type(), p.player()), true);
                case "unban" -> cache.put(key("ban", p.player()), false);
                case "unmute" -> cache.put(key("mute", p.player()), false);
                default -> { }
            }
            plugin.getLogger().info("[PROXY] " + p.type() + " " + p.player() + " -> OK");
        } else {
            notifyStaff(p, "punishments.proxy.failed");
        }
    }

    /** Son bilinen durum (yoksa false). type: "ban" | "mute". */
    boolean cached(String type, String player) {
        return cache.getOrDefault(key(type, player), false);
    }

    private void notifyStaff(Pending p, String messageKey) {
        plugin.getLogger().warning("[PROXY] " + p.type() + " " + p.player() + " -> " + messageKey);
        if (p.staff() == null || !(plugin instanceof ReportSystemSpigot rs)) {
            return;
        }
        Player staff = Bukkit.getPlayerExact(p.staff());
        if (staff != null && staff.isOnline()) {
            staff.sendMessage(rs.getMessageManager().colorize(
                    rs.getMessageManager().getMessage(messageKey)
                            .replace("%type%", p.type())
                            .replace("%player%", p.player())));
        }
    }

    private static String key(String type, String player) {
        return type + ":" + player.toLowerCase(Locale.ROOT);
    }
}
