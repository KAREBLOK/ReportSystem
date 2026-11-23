package com.reportsystem.spigot.punishment.providers;

import com.reportsystem.common.punishment.DurationFormat;
import com.reportsystem.common.punishment.PunishmentProvider;
import com.reportsystem.spigot.ReportSystemSpigot;
import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InternalPunishmentProvider implements PunishmentProvider, Listener {

    private final JavaPlugin plugin;
    private final Map<String, Long> mutedPlayers = new ConcurrentHashMap<>();
    private final Map<String, List<String>> playerWarnings = new ConcurrentHashMap<>();
    /** Kalıcılık (mute + uyarı): DB G/Ç arka planda; okumalar yukarıdaki bellek önbelleklerinden. Null = yalnız RAM. */
    private PunishmentStore store;

    private com.reportsystem.spigot.managers.MessageManager getMessageManager() {
        return ((ReportSystemSpigot) plugin).getMessageManager();
    }

    public InternalPunishmentProvider(JavaPlugin plugin) {
        this.plugin = plugin;

        // Mute/uyarı kalıcılığı: tabloları oluştur, süresi dolanları sil, aktifleri belleğe yükle (hepsi arka planda)
        try {
            com.reportsystem.common.database.Database db = ((ReportSystemSpigot) plugin).getDatabaseManager();
            if (db != null) {
                this.store = new PunishmentStore(plugin, db);
                this.store.initAsync(this::loadMutes, this::loadWarnings);
            } else {
                plugin.getLogger().warning("[PUNISHMENT] Veritabanı yok, mute/uyarılar yalnız bellekte tutulacak.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PUNISHMENT] Kalıcı depo başlatılamadı: " + e.getMessage());
        }

        // Chat listener'ı kaydet
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Mute listesini temizleme task'ı (her dakika)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredMutes, 0L, 1200L);
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long durationMillis) {
        if (durationMillis > 0) {
            return tempBan(playerName, reason, punisher, durationMillis);
        } else {
            return permBan(playerName, reason, punisher);
        }
    }

    @Override
    public boolean tempBan(String playerName, String reason, String punisher, long durationMillis) {
        try {
            Date expires = new Date(DurationFormat.expiryFrom(System.currentTimeMillis(), durationMillis));

            // Ban mesajı
            String banMessage = getMessageManager().colorize(
                    getMessageManager().getMessage("punishments.ban.screen-temp")
                            .replace("%reason%", reason)
                            .replace("%duration%", formatDuration(durationMillis))
                            .replace("%expires%", formatDate(expires))
                            .replace("%staff%", punisher));

            // Tek yazım: PROFILE ban listesi (UUID bazlı; HTTP'siz). NAME listesi Paper'da aynı listedir.
            if (!addProfileBan(playerName, reason, expires, punisher)) {
                return false;
            }

            // Oyuncu online ise at
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                kickTarget(target, banMessage);
            }

            // Log
            plugin.getLogger().info("[BAN] " + playerName + " banned by " + punisher +
                    " for " + formatDuration(durationMillis) + ". Reason: " + reason);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error banning player: " + e.getMessage());
            return false;
        }
    }

    /**
     * Oyuncuyu atar. Normalde ana thread'e ertelenir; eklenti kapanırken (onDisable) planlayıcı
     * görev kabul etmez (IllegalPluginAccessException) -> o durumda doğrudan atılır [THR-17].
     */
    private void kickTarget(Player target, String message) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> target.kickPlayer(message));
        } else if (Bukkit.isPrimaryThread()) {
            target.kickPlayer(message);
        } else {
            // Kapanışta async thread'den atılamaz (AsyncCatcher); giriş engeli ban kaydından gelir.
            plugin.getLogger().warning("[KICK] Plugin disabled on async thread; skipped kick of " + target.getName());
        }
    }

    @Override
    public boolean permBan(String playerName, String reason, String punisher) {
        try {
            // Ban mesajı
            String banMessage = getMessageManager().colorize(
                    getMessageManager().getMessage("punishments.ban.screen-perm")
                            .replace("%reason%", reason)
                            .replace("%staff%", punisher)
                            .replace("%discord_url%", getMessageManager().getMessage("punishments.discord-url")));

            // Tek yazım: PROFILE ban listesi (null = kalıcı)
            if (!addProfileBan(playerName, reason, null, punisher)) {
                return false;
            }

            // Oyuncu online ise at
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                kickTarget(target, banMessage);
            }

            // Log
            plugin.getLogger().info("[PERMBAN] " + playerName + " permanently banned by " +
                    punisher + ". Reason: " + reason);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error permanently banning player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long durationMillis) {
        try {
            String playerNameLower = playerName.toLowerCase(Locale.ROOT);

            long muteEnd = durationMillis > 0 ? DurationFormat.expiryFrom(System.currentTimeMillis(), durationMillis) : Long.MAX_VALUE; // MAX = kalıcı
            mutedPlayers.put(playerNameLower, muteEnd);
            if (store != null) store.saveMute(playerNameLower, muteEnd, reason, punisher);

            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                String muteMsg = getMessageManager().getMessage("punishments.mute.player-message")
                        .replace("%reason%", reason)
                        .replace("%duration%", durationMillis > 0 ? formatDuration(durationMillis) : getMessageManager().getMessage("misc.duration.permanent"))
                        .replace("%staff%", punisher);
                for (String line : muteMsg.split("\n")) {
                    target.sendMessage(getMessageManager().colorize(line));
                }

                // Ses efekti
                target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
            }

            // Log
            plugin.getLogger().info("[MUTE] " + playerName + " muted by " + punisher +
                    " for " + (durationMillis > 0 ? formatDuration(durationMillis) : "permanent") +
                    ". Reason: " + reason);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error muting player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        try {
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                String kickMessage = getMessageManager().colorize(
                        getMessageManager().getMessage("punishments.kick.screen")
                                .replace("%reason%", reason)
                                .replace("%staff%", punisher));

                kickTarget(target, kickMessage);

                // Log
                plugin.getLogger().info("[KICK] " + playerName + " kicked by " + punisher +
                        ". Reason: " + reason);

                return true;
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Error kicking player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        try {
            // Uyarıyı kaydet
            String playerNameLower = playerName.toLowerCase(Locale.ROOT);
            List<String> warnings = playerWarnings.computeIfAbsent(playerNameLower, k -> new CopyOnWriteArrayList<>());
            Date now = new Date();
            warnings.add(reason + " - " + punisher + " (" + now + ")");
            if (store != null) store.addWarning(playerNameLower, reason, punisher, now.getTime());

            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                String warnMsg = getMessageManager().getMessage("punishments.warn.player-message")
                        .replace("%reason%", reason)
                        .replace("%staff%", punisher)
                        .replace("%count%", String.valueOf(warnings.size()));
                for (String line : warnMsg.split("\n")) {
                    target.sendMessage(getMessageManager().colorize(line));
                }

                // Ses efekti ve title
                target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                target.sendTitle(
                        getMessageManager().colorize(getMessageManager().getMessage("punishments.warn.title")),
                        getMessageManager().colorize(getMessageManager().getMessage("punishments.warn.subtitle")),
                        10, 60, 20);
            }

            // Log
            plugin.getLogger().info("[WARN] " + playerName + " warned by " + punisher +
                    ". Reason: " + reason + " (Total warnings: " + warnings.size() + ")");

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error warning player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unban(String playerName) {
        try {
            // PROFILE ban listesinden kaldır (tek liste)
            boolean unbanned = removeProfileBan(playerName);

            if (unbanned) {
                plugin.getLogger().info("[UNBAN] " + playerName + " has been unbanned.");
            }
            return unbanned;
        } catch (Exception e) {
            plugin.getLogger().severe("Error unbanning player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unmute(String playerName) {
        try {
            boolean result = mutedPlayers.remove(playerName.toLowerCase(Locale.ROOT)) != null;
            if (store != null) store.deleteMute(playerName);
            if (result) {
                plugin.getLogger().info("[UNMUTE] " + playerName + " has been unmuted.");

                Player target = Bukkit.getPlayerExact(playerName);
                if (target != null && target.isOnline()) {
                    target.sendMessage(getMessageManager().colorize(getMessageManager().getMessage("punishments.mute.unmuted-player")));
                }
            }
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error unmuting player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isBanned(String playerName) {
        return isProfileBanned(playerName);
    }

    @Override
    public boolean isMuted(String playerName) {
        Long muteEnd = mutedPlayers.get(playerName.toLowerCase(Locale.ROOT));
        if (muteEnd == null) return false;

        if (muteEnd == Long.MAX_VALUE) return true; // Kalıcı mute

        if (System.currentTimeMillis() < muteEnd) {
            return true;
        } else {
            mutedPlayers.remove(playerName.toLowerCase(Locale.ROOT));
            if (store != null) store.deleteMute(playerName);
            return false;
        }
    }

    /**
     * Chat event handler - mute kontrolü için
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (isMuted(player.getName())) {
            event.setCancelled(true);

            Long muteEnd = mutedPlayers.get(player.getName().toLowerCase(Locale.ROOT));
            String timeLeft = "";

            if (muteEnd != null && muteEnd != Long.MAX_VALUE) {
                long remaining = muteEnd - System.currentTimeMillis();
                if (remaining > 0) {
                    timeLeft = getMessageManager().getMessage("punishments.mute.time-remaining").replace("%time%", formatDuration(remaining));
                }
            } else if (muteEnd == Long.MAX_VALUE) {
                timeLeft = getMessageManager().getMessage("punishments.mute.time-permanent");
            }

            player.sendMessage(getMessageManager().colorize(
                    getMessageManager().getMessage("punishments.mute.chat-blocked")
                            .replace("%time_left%", timeLeft)));
            player.sendMessage(getMessageManager().colorize(getMessageManager().getMessage("punishments.mute.chat-blocked-detail")));

            // Ses efekti
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
        }
    }

    /**
     * Süresi dolmuş mute'ları temizler
     */
    private void cleanupExpiredMutes() {
        long now = System.currentTimeMillis();
        mutedPlayers.entrySet().removeIf(entry -> {
            Long muteEnd = entry.getValue();
            return muteEnd != Long.MAX_VALUE && muteEnd < now;
        });
        if (store != null) store.purgeExpiredAsync();
    }

    /** DB'den yüklenen aktif mute'ları belleğe alır (arka plan thread'i; ConcurrentHashMap güvenli). */
    private void loadMutes(List<PunishmentStore.MuteRow> rows) {
        long now = System.currentTimeMillis();
        for (PunishmentStore.MuteRow row : rows) {
            if (row.expires() == Long.MAX_VALUE || row.expires() > now) {
                // Yükleme sırasında verilmiş daha yeni bir mute'u ezme
                mutedPlayers.putIfAbsent(row.player().toLowerCase(Locale.ROOT), row.expires());
            }
        }
        if (!rows.isEmpty()) {
            plugin.getLogger().info("[PUNISHMENT] " + rows.size() + " mute kaydı veritabanından yüklendi.");
        }
    }

    /**
     * DB'den yüklenen uyarıları belleğe alır (kayıt biçimi warn() ile aynı). Liste DB satırlarından kurulur;
     * yükleme bitmeden verilmiş (bellekte olup DB satırlarında bulunmayan) uyarılar sona eklenir, böylece
     * aynı uyarı iki kez görünmez ve hiçbiri kaybolmaz.
     */
    private void loadWarnings(List<PunishmentStore.WarnRow> rows) {
        Map<String, List<String>> loaded = new HashMap<>();
        for (PunishmentStore.WarnRow row : rows) {
            loaded.computeIfAbsent(row.player().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(row.reason() + " - " + row.staff() + " (" + new Date(row.created()) + ")");
        }
        for (Map.Entry<String, List<String>> e : playerWarnings.entrySet()) {
            List<String> list = loaded.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            for (String mem : e.getValue()) {
                if (!list.contains(mem)) list.add(mem);
            }
        }
        for (Map.Entry<String, List<String>> e : loaded.entrySet()) {
            playerWarnings.put(e.getKey(), new CopyOnWriteArrayList<>(e.getValue()));
        }
    }

    /** Eklenti kapanırken bekleyen DB yazmalarını bitirir (store yoksa no-op). */
    public void shutdown() {
        if (store != null) {
            store.shutdown();
        }
    }

    /**
     * Süreyi formatlar
     */
    private String formatDuration(long millis) {
        if (millis <= 0) return "0 " + getMessageManager().getMessage("misc.time.seconds");

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        String daysWord = getMessageManager().getMessage("misc.time.days");
        String hoursWord = getMessageManager().getMessage("misc.time.hours");
        String minutesWord = getMessageManager().getMessage("misc.time.minutes");
        String secondsWord = getMessageManager().getMessage("misc.time.seconds");

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

    /**
     * Tarihi formatlar
     */
    private String formatDate(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(date);
    }

    /**
     * Oyuncunun uyarı sayısını döndürür
     */
    public int getWarningCount(String playerName) {
        List<String> warnings = playerWarnings.get(playerName.toLowerCase(Locale.ROOT));
        return warnings != null ? warnings.size() : 0;
    }

    /**
     * Oyuncunun uyarılarını döndürür
     */
    public List<String> getWarnings(String playerName) {
        return playerWarnings.getOrDefault(playerName.toLowerCase(Locale.ROOT), new ArrayList<>());
    }

    /** Paper PROFILE ban listesi (UUID bazlı). NAME tipi aynı listeye çözüldüğü için kullanılmaz. */
    private static ProfileBanList profileBanList() {
        return Bukkit.getBanList(BanListType.PROFILE);
    }

    /**
     * PROFILE ban listesine UUID bazlı ekler. Oyuncu çevrimiçi/önbellekte yoksa Mojang'a ağ sorgusu
     * ana thread'de yapılmaz (THR) -> false döner ve uyarı loglanır. addBan null dönerse (sessiz başarısızlık)
     * de false.
     */
    private boolean addProfileBan(String playerName, String reason, Date expires, String punisher) {
        try {
            PlayerProfile profile = findKnownProfile(playerName);
            if (profile == null) {
                plugin.getLogger().warning("[BAN] Player '" + playerName + "' is not online or in the server "
                        + "profile cache (never seen); ban NOT applied (no blocking Mojang lookup on main thread).");
                return false;
            }
            Object entry = profileBanList().addBan(profile, reason, expires, punisher);
            if (entry == null) {
                plugin.getLogger().warning("[BAN] Server returned no ban entry for '" + playerName + "'; ban not applied.");
                return false;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[BAN] Profile ban could not be added: " + e.getMessage());
            return false;
        }
    }

    /**
     * PROFILE ban listesinden kaldırır. Profil biliniyorsa UUID ile; değilse listedeki girdilerin
     * adı üzerinden tarar (HTTP yok).
     */
    private boolean removeProfileBan(String playerName) {
        try {
            PlayerProfile profile = findKnownProfile(playerName);
            if (profile != null && profileBanList().isBanned(profile)) {
                profileBanList().pardon(profile);
                return true;
            }
            PlayerProfile listed = findListedProfile(playerName);
            if (listed != null) {
                profileBanList().pardon(listed);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[UNBAN] Profile unban error: " + e.getMessage());
        }
        return false;
    }

    /**
     * PROFILE ban listesinde banlı mı kontrol eder (HTTP yok).
     */
    private boolean isProfileBanned(String playerName) {
        try {
            PlayerProfile profile = findKnownProfile(playerName);
            if (profile != null && profileBanList().isBanned(profile)) {
                return true;
            }
            return findListedProfile(playerName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Ban uygulanabilir mi: oyuncu bilinen (çevrimiçi/önbellekte) veya çevrimdışı-mod yedeği geçerli (HTTP yok). */
    public boolean isKnownPlayer(String playerName) {
        return findKnownProfile(playerName) != null;
    }

    /**
     * Bilinen (çevrimiçi/önbellekteki) oyuncunun UUID'li profili. Bulunamazsa ve sunucu gerçekten
     * çevrimdışı modda (proxy forwarding yok) ise deterministik "OfflinePlayer:" UUID'si; yoksa null.
     */
    private PlayerProfile findKnownProfile(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        OfflinePlayer op = findKnownPlayer(playerName);
        if (op != null) {
            String name = op.getName() != null ? op.getName() : playerName;
            return Bukkit.createPlayerProfile(op.getUniqueId(), name);
        }
        if (isTrueOfflineMode()) {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
            return Bukkit.createPlayerProfile(uuid, playerName);
        }
        return null;
    }

    /** online-mode=false VE bungeecord/velocity forwarding kapalı: UUID'ler yerel offline UUID'dir. */
    private boolean isTrueOfflineMode() {
        try {
            if (Bukkit.getOnlineMode()) return false;
            if (Bukkit.spigot().getSpigotConfig().getBoolean("settings.bungeecord", false)) return false;
            File paperGlobal = new File(Bukkit.getWorldContainer(), "config/paper-global.yml");
            if (paperGlobal.isFile()) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(paperGlobal);
                if (yml.getBoolean("proxies.velocity.enabled", false)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Ban listesi girdilerinde ada (equalsIgnoreCase) göre profil arar; ağ sorgusu yok. */
    private PlayerProfile findListedProfile(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        for (BanEntry<?> entry : profileBanList().getEntries()) {
            Object target = entry.getBanTarget();
            if (target instanceof PlayerProfile) {
                PlayerProfile p = (PlayerProfile) target;
                if (p.getName() != null && p.getName().equalsIgnoreCase(playerName)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Ada göre oyuncuyu bulur; Mojang'a ağ sorgusu YAPMAZ (isimle OfflinePlayer araması ana thread'i
     * bloklayabilir). Önce çevrimiçi oyuncu, sonra sunucunun usercache'i (Paper API). Hiç görülmemiş
     * oyuncu için null — ban başarısız sayılır (addProfileBan false döner).
     */
    private OfflinePlayer findKnownPlayer(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        OfflinePlayer op = Bukkit.getPlayerExact(playerName);
        if (op == null) op = Bukkit.getOfflinePlayerIfCached(playerName);
        return op;
    }
}
