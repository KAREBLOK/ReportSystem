package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Replay / Overwatch izleyicisinin gercek durumunun (envanter, oyun modu, can, konum ...)
 * CÖKMEYE DAYANIKLI yedegi.
 *
 * Yedek oyuncunun KENDI PersistentDataContainer'ina yazilir. PDC playerdata dosyasiyla
 * AYNI dosyada, AYNI anda kaydedilir; bu yuzden:
 *  - Sunucu replay sirasinda coker ve diske bos/kontrol esyali envanter yazilmissa,
 *    yedek de ayni kayitta vardir → sonraki giriste geri yuklenir.
 *  - Geri yuklemeden sonra yedek AYNI kod blogunda silinir → playerdata bir sonraki
 *    kayitta hem dogru envanteri hem "yedek yok" bilgisini birlikte yazar; ayri dosya
 *    yedeklerindeki "geri yuklendi ama yedek silinemedi" rollback dupe'u olusamaz.
 *
 * Icerik STRING degil BYTE_ARRAY: NBT dizgesi 65535 bayt (modified UTF-8) ile sinirli;
 * dolu shulker'li bir envanterin YAML'i bunu asip playerdata kaydini bozabilir.
 *
 * Tum metotlar ANA THREAD'de cagrilmalidir. Dosya / DB I/O yoktur.
 */
public final class ViewerBackup implements Listener {

    public static final String OWNER_REPLAY = "replay";
    public static final String OWNER_OVERWATCH = "overwatch";

    private final ReportSystemSpigot plugin;
    private final NamespacedKey dataKey;
    private final NamespacedKey ownerKey;

    // Ayristirilamayan yedek buraya tasinir (karantina): asil anahtari tikamaz, veri elle kurtarilabilir.
    // M-5: anahtar zaman damgalidir (viewer_backup_corrupt_<epochMillis>) → dolu bir karantina yeni ham
    // yedegin karantinaya alinmasini engellemez, eski karantina da ezilmez.
    private static final String CORRUPT_KEY_PREFIX = "viewer_backup_corrupt_";

    public ViewerBackup(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.dataKey = new NamespacedKey(plugin, "viewer_backup");
        this.ownerKey = new NamespacedKey(plugin, "viewer_backup_owner");
    }

    private static final int MAX_QUARANTINE_KEYS = 5;

    /**
     * K-g: karantina yazilacaksa anahtari dondurur; ayni bayt dizisi zaten bir karantina anahtarinda
     * varsa veya karantina anahtari sayisi {@link #MAX_QUARANTINE_KEYS}'e ulastiysa null (yazma).
     */
    private NamespacedKey quarantineKeyFor(PersistentDataContainer pdc, byte[] bytes) {
        int count = 0;
        for (NamespacedKey k : pdc.getKeys()) {
            if (!k.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))
                    || !k.getKey().startsWith(CORRUPT_KEY_PREFIX)) continue;
            count++;
            byte[] existing = pdc.get(k, PersistentDataType.BYTE_ARRAY);
            if (existing != null && java.util.Arrays.equals(existing, bytes)) return null;
        }
        if (count >= MAX_QUARANTINE_KEYS) return null;
        return newQuarantineKey(pdc);
    }

    /** Bu oyuncunun PDC'sinde henuz kullanilmayan, zaman damgali yeni bir karantina anahtari uretir. */
    private NamespacedKey newQuarantineKey(PersistentDataContainer pdc) {
        long stamp = System.currentTimeMillis();
        NamespacedKey key = new NamespacedKey(plugin, CORRUPT_KEY_PREFIX + stamp);
        int suffix = 1;
        while (pdc.has(key, PersistentDataType.BYTE_ARRAY)) {
            key = new NamespacedKey(plugin, CORRUPT_KEY_PREFIX + stamp + "_" + suffix++);
        }
        return key;
    }

    /**
     * Oyuncunun mevcut durumunu yedekler. PDC'de zaten bir yedek varsa DOKUNMAZ
     * (putIfAbsent anlami): "Tekrar Izle" gibi akislarda envanter zaten temizlenmisken
     * ilk (gercek) yedegin uzerine bos envanter yazilmasini engeller.
     * Konum olarak oyuncunun o anki konumu yazilir.
     */
    public void saveIfAbsent(Player player, String owner) {
        saveIfAbsent(player, owner, null);
    }

    /**
     * {@link #saveIfAbsent(Player, String)} ile ayni; ancak konum olarak {@code originalLocation}
     * (null degilse ve dunyasi varsa) yazilir. Dunya degistiren replay'de oyuncu yedek anında
     * zaten replay dunyasinda olabilir; cokme sonrasi baslangic noktasina donmeli.
     */
    public void saveIfAbsent(Player player, String owner, Location originalLocation) {
        if (player == null || owner == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(dataKey, PersistentDataType.BYTE_ARRAY)) {
            plugin.debug("[VIEWER-BACKUP] Backup already present for " + player.getName() + ", keeping the first one");
            return;
        }

        try {
            YamlConfiguration yaml = new YamlConfiguration();
            ItemStack[] contents = player.getInventory().getContents();
            yaml.set("size", contents.length);
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null) {
                    yaml.set("inv." + i, item);
                }
            }
            yaml.set("gamemode", player.getGameMode().name());
            yaml.set("health", player.getHealth());
            yaml.set("food", player.getFoodLevel());
            yaml.set("saturation", (double) player.getSaturation());
            yaml.set("allowFlight", player.getAllowFlight());
            yaml.set("flying", player.isFlying());
            yaml.set("fireTicks", player.getFireTicks());

            Location loc = (originalLocation != null && originalLocation.getWorld() != null)
                    ? originalLocation : player.getLocation();
            if (loc.getWorld() != null) {
                yaml.set("loc.world", loc.getWorld().getUID().toString());
                yaml.set("loc.x", loc.getX());
                yaml.set("loc.y", loc.getY());
                yaml.set("loc.z", loc.getZ());
                yaml.set("loc.yaw", (double) loc.getYaw());
                yaml.set("loc.pitch", (double) loc.getPitch());
            }

            byte[] bytes = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
            pdc.set(dataKey, PersistentDataType.BYTE_ARRAY, bytes);
            pdc.set(ownerKey, PersistentDataType.STRING, owner);
            plugin.debug("[VIEWER-BACKUP] Saved backup for " + player.getName() +
                    " (owner=" + owner + ", " + bytes.length + " bytes)");
        } catch (Exception e) {
            // Yedek yazilamadiysa akis eskisi gibi yalniz RAM kopyasiyla devam eder.
            plugin.getLogger().log(Level.SEVERE,
                    "[VIEWER-BACKUP] Failed to save crash backup for " + player.getName(), e);
        }
    }

    /** Yedegi yalniz kayitli sahip {@code owner} ise siler. */
    public void clear(Player player, String owner) {
        if (player == null || owner == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String current = pdc.get(ownerKey, PersistentDataType.STRING);
        if (!owner.equals(current)) return;
        pdc.remove(dataKey);
        pdc.remove(ownerKey);
        plugin.debug("[VIEWER-BACKUP] Cleared backup for " + player.getName() + " (owner=" + owner + ")");
    }

    /**
     * Yedegin sahibini {@code fromOwner}'dan {@code toOwner}'a devreder (icerige dokunmaz).
     * Ornek: Overwatch baslatmasi basarisiz oldu ama arada baslayan normal replay envanteri
     * temizledi → yedek artik o replay'indir; replay bitisinde clear(OWNER_REPLAY) siler.
     */
    public void retag(Player player, String fromOwner, String toOwner) {
        if (player == null || fromOwner == null || toOwner == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String current = pdc.get(ownerKey, PersistentDataType.STRING);
        if (!fromOwner.equals(current) || !pdc.has(dataKey, PersistentDataType.BYTE_ARRAY)) return;
        pdc.set(ownerKey, PersistentDataType.STRING, toOwner);
        plugin.debug("[VIEWER-BACKUP] Backup of " + player.getName() + " retagged " + fromOwner + " -> " + toOwner);
    }

    /** Sahibine bakmadan yedegi siler (izleme oturumunun en dis ucu geri yuklendi). */
    public void clearAny(Player player) {
        if (player == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(dataKey, PersistentDataType.BYTE_ARRAY) && !pdc.has(ownerKey, PersistentDataType.STRING)) {
            return;
        }
        pdc.remove(dataKey);
        pdc.remove(ownerKey);
        plugin.debug("[VIEWER-BACKUP] Cleared backup for " + player.getName() + " (any owner)");
    }

    /**
     * PDC'de yedek varsa (onceki oturum cokmus) oyuncuya geri uygular ve yedegi siler.
     * Envanter setContents ile DEGISTIRILIR (addItem yok) → tekrar cagrilsa bile dupe olmaz.
     * Ayristirilamayan yedek zaman damgali karantina anahtarina ({@code viewer_backup_corrupt_<ms>}) tasinir ve
     * SEVERE loglanir (yeni yedekleri tikamaz). Envanter yazma hatasinda yedek SILINMEZ (bir
     * sonraki giriste yeniden denenir); envanter yazildiktan sonra yedek hemen silinir.
     *
     * @return yedek bulunup basariyla uygulandiysa true
     */
    public boolean restoreIfPresent(Player player) {
        if (player == null) return false;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        byte[] bytes = pdc.get(dataKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) {
            // Sahipsiz artik owner anahtari kaldiysa temizle
            if (pdc.has(ownerKey, PersistentDataType.STRING)) {
                pdc.remove(ownerKey);
            }
            return false;
        }

        // 1) Once TAMAMEN ayristir — yarim uygulama olmasin.
        // Ayristirilamayan yedek karantinaya tasinir: asil anahtar bos kalir, boylece
        // saveIfAbsent sonraki oturumlarda yeni yedek alabilir (kalici tikac olmaz).
        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack[] contents = null;
        GameMode gameMode = null;
        Location location = null;
        try {
            yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8));

            int size = yaml.getInt("size", player.getInventory().getSize());
            if (size <= 0) size = player.getInventory().getSize();
            contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                contents[i] = yaml.getItemStack("inv." + i);
            }

            String gmName = yaml.getString("gamemode");
            if (gmName != null) {
                try {
                    gameMode = GameMode.valueOf(gmName);
                } catch (IllegalArgumentException ignored) {
                    // bilinmeyen mod — oyun moduna dokunma
                }
            }

            String worldId = yaml.getString("loc.world");
            if (worldId != null) {
                World world = null;
                try {
                    world = Bukkit.getWorld(UUID.fromString(worldId));
                } catch (IllegalArgumentException ignored) {
                    // bozuk UUID — konuma dokunma
                }
                if (world != null) {
                    location = new Location(world,
                            yaml.getDouble("loc.x"), yaml.getDouble("loc.y"), yaml.getDouble("loc.z"),
                            (float) yaml.getDouble("loc.yaw"), (float) yaml.getDouble("loc.pitch"));
                }
            }
        } catch (Exception parseEx) {
            NamespacedKey corruptKey = newQuarantineKey(pdc);
            pdc.set(corruptKey, PersistentDataType.BYTE_ARRAY, bytes);
            pdc.remove(dataKey);
            pdc.remove(ownerKey);
            plugin.getLogger().log(Level.SEVERE,
                    "[VIEWER-BACKUP] Crash backup of " + player.getName() + " could not be parsed; moved to '" +
                            corruptKey + "' (" + bytes.length + " bytes) for manual recovery", parseEx);
            return false;
        }

        // K3: cozulemeyen esyalar (getItemStack null — ör. surum degisikligi / bilinmeyen esya)
        // eskiden sessizce dusuyordu ve yedek silindigi icin geri donusu yoktu. Kaydedilen yuva
        // sayisiyla cozulen esya sayisini karsilastir; eksik varsa ham yedegi karantinaya kopyala
        // (zaman damgali yeni anahtar → mevcut karantina ezilmez, dolu olsa da yeni yedek alinir) ve SEVERE logla. Geri yukleme + ana anahtar silme
        // YINE yapilir: aksi halde ayni yedek her giriste tekrar uygulanir (rollback dupe'u).
        org.bukkit.configuration.ConfigurationSection invSection = yaml.getConfigurationSection("inv");
        if (invSection != null) {
            java.util.Set<String> savedSlots = invSection.getKeys(false);
            java.util.List<String> lostSlots = new java.util.ArrayList<>();
            for (String slotKey : savedSlots) {
                boolean resolved = false;
                try {
                    int idx = Integer.parseInt(slotKey);
                    resolved = idx >= 0 && idx < contents.length && contents[idx] != null;
                } catch (NumberFormatException ignored) {
                    // sayisal olmayan yuva anahtari — kayip say
                }
                if (!resolved) lostSlots.add(slotKey);
            }
            if (!lostSlots.isEmpty()) {
                NamespacedKey corruptKey = quarantineKeyFor(pdc, bytes);
                if (corruptKey == null) {
                    plugin.getLogger().severe("[VIEWER-BACKUP] Crash backup of " + player.getName() + ": " +
                            lostSlots.size() + " of " + savedSlots.size() + " item(s) could not be deserialized (slots " +
                            String.join(", ", lostSlots) + "), but the quarantine is full (" + MAX_QUARANTINE_KEYS +
                            " keys) or already holds this exact backup; NOT writing a new quarantine key.");
                } else {
                pdc.set(corruptKey, PersistentDataType.BYTE_ARRAY, bytes);
                plugin.getLogger().severe("[VIEWER-BACKUP] Crash backup of " + player.getName() + ": " +
                        lostSlots.size() + " of " + savedSlots.size() + " item(s) could not be deserialized (slots " +
                        String.join(", ", lostSlots) + "), raw backup quarantined to '" + corruptKey + "'" +
                        " (" + bytes.length + " bytes) for manual recovery. WARNING: the quarantined backup ALSO" +
                        " contains the items that were restored now; when recovering manually give back ONLY the" +
                        " missing slots (" + String.join(", ", lostSlots) + ") to avoid duplicating items.");
                }
            }
        }

        try {
            // 2) Uygula — envanter DEGISTIRILIR (addItem degil)
            int invSize = player.getInventory().getSize();
            if (contents.length != invSize) {
                ItemStack[] resized = new ItemStack[invSize];
                System.arraycopy(contents, 0, resized, 0, Math.min(contents.length, invSize));
                contents = resized;
            }
            player.getInventory().setContents(contents);
            player.updateInventory();

            // 3) Envanter yazildi → yedegi HEMEN, AYNI blokta sil. Asagidaki durum
            // adimlarindan biri hata verse bile yedek kalmamali: kalirsa sonraki giriste
            // eski envanter yeniden yazilir (rollback dupe'u).
            pdc.remove(dataKey);
            pdc.remove(ownerKey);

            try {
                if (gameMode != null) {
                    player.setGameMode(gameMode);
                }
                boolean allowFlight = yaml.getBoolean("allowFlight", player.getAllowFlight());
                player.setAllowFlight(allowFlight);
                if (allowFlight) {
                    player.setFlying(yaml.getBoolean("flying", false));
                }
                double health = yaml.getDouble("health", player.getHealth());
                if (health > 0) {
                    double maxHealth = com.reportsystem.spigot.utils.AttributeCompat.maxHealth(player);
                    player.setHealth(Math.min(health, maxHealth));
                }
                player.setFoodLevel(yaml.getInt("food", player.getFoodLevel()));
                player.setSaturation((float) yaml.getDouble("saturation", player.getSaturation()));
                player.setFireTicks(yaml.getInt("fireTicks", 0));
                player.setFallDistance(0f);
            } catch (Exception stateEx) {
                // Envanter zaten geri geldi; durum alanlarindan biri uygulanamadi.
                plugin.getLogger().log(Level.WARNING,
                        "[VIEWER-BACKUP] Inventory restored but player state could not be fully restored for " +
                                player.getName(), stateEx);
            }

            // 4) Konum: giris/enable aninda isinlama guvenilir degil → 1 tick sonra
            if (location != null && plugin.isEnabled()) {
                final Location target = location;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(target);
                        player.setFallDistance(0f);
                    }
                });
            }

            plugin.getLogger().info("[VIEWER-BACKUP] crash backup restored for " + player.getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[VIEWER-BACKUP] Failed to apply crash backup for " + player.getName() +
                            " - backup kept for the next attempt", e);
            return false;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        restoreIfPresent(event.getPlayer());
    }
}
