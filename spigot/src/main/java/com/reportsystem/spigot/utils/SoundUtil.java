package com.reportsystem.spigot.utils;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ses anahtarı uyumluluk katmanı.
 *
 * Derleme hedefi 1.21 olduğundan Sound sabitlerine doğrudan erişmek, sabiti yeniden
 * adlandırılmış/kaldırılmış sunucularda NoSuchFieldError fırlatır (ör. 1.21.6+'da
 * ENTITY_LEASH_KNOT_PLACE -> ITEM_LEAD_TIED). Bu sınıf sesleri "minecraft:..." anahtarı
 * üzerinden çözer ve Player#playSound(Location, String, float, float) ile çalar;
 * bilinmeyen anahtar istemcide sessizce yok sayılır, istisna fırlatmaz.
 *
 * Thread güvenliği: herhangi bir thread'den çağrılabilir (async WebReplay dönüştürücüsü de kullanır).
 * İlk kullanımda kurulan eski-ad haritası yalnız okunan (başlangıçtan sonra değişmeyen) Registry.SOUNDS'tan
 * üretilir ve değiştirilemez harita olarak volatile alana yayımlanır. Kilit yoktur: eşzamanlı ilk
 * çağrılarda harita birden fazla kez (pratikte en fazla iki-üç kez) kurulabilir; sonuç aynı olduğundan
 * zararsızdır, yalnız tekrarlanan tarama maliyeti vardır. Ses ÇALMA (Player#playSound) Bukkit kuralı
 * gereği yine ana thread'den yapılmalıdır.
 */
public final class SoundUtil {

    // Eski enum ses adı -> registry anahtarı (tembel; ilk kullanımda, çağıran thread'de kurulur)
    private static volatile Map<String, String> legacySoundKeys;

    private SoundUtil() {
    }

    /**
     * Ses adını/anahtarını çalınabilir registry anahtarına çevirir.
     * - null/boş -> null
     * - ':' veya '.' içeriyorsa (ör. "minecraft:entity.player.hurt") -> ROOT küçük harf
     * - aksi halde eski enum adı (ENTITY_PLAYER_HURT) -> registry haritası;
     *   haritada yoksa eski '_' -> '.' dönüşümü, yalnız registry'de gerçekten varsa.
     * Çözülemezse null döner.
     */
    public static String resolveKey(String nameOrKey) {
        if (nameOrKey == null || nameOrKey.isEmpty()) return null;
        String soundKey = nameOrKey.toLowerCase(Locale.ROOT);
        if (soundKey.contains(":") || soundKey.indexOf('.') >= 0) {
            return soundKey;
        }
        // Eski enum adı (ENTITY_PLAYER_HURT / BLOCK_NOTE_BLOCK_PLING) -> registry anahtarı.
        // Kör '_' -> '.' dönüşümü kelime içi alt çizgiyi bozar (note_block), önce haritaya bak.
        String mapped = legacySoundKeys().get(soundKey.toUpperCase(Locale.ROOT));
        if (mapped != null) {
            return mapped;
        }
        // Yedek: eski dönüşüm; yalnız registry'de gerçekten varsa çal
        String fallback = soundKey.replace('_', '.');
        try {
            NamespacedKey fallbackKey = NamespacedKey.fromString(fallback);
            if (fallbackKey != null && Registry.SOUNDS.get(fallbackKey) != null) {
                return fallback;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Registry erişilemiyor — çözülemedi say
        }
        debug("[SOUND] Unknown legacy sound name, skipped: " + nameOrKey);
        return null;
    }

    /**
     * Registry'de bulunan ilk anahtarı döndürür (ör. yeni ad önce, eski ad sonra).
     * Hiçbiri yoksa (veya registry erişilemezse) ilk aday döner; istemci bilinmeyeni sessizce yok sayar.
     */
    public static String firstExisting(String... keys) {
        if (keys == null || keys.length == 0) return null;
        for (String k : keys) {
            if (k == null || k.isEmpty()) continue;
            try {
                NamespacedKey nsKey = NamespacedKey.fromString(k.toLowerCase(Locale.ROOT));
                if (nsKey != null && Registry.SOUNDS.get(nsKey) != null) {
                    return k;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Bu aday çözülemedi — sıradakini dene
            }
        }
        return keys[0];
    }

    /** Ses adını/anahtarını çözüp oyuncuya çalar; çözülemezse hiçbir şey yapmaz. */
    public static void play(Player player, Location location, String nameOrKey, float volume, float pitch) {
        if (player == null || location == null) return;
        String key = resolveKey(nameOrKey);
        if (key == null) return;
        player.playSound(location, key, volume, pitch);
    }

    /**
     * "ESKİ_ENUM_ADI" -> "minecraft:anahtar" haritası (tembel, bir kez kurulur).
     * Registry.SOUNDS taranır; anahtar Registry#getKey ile alınır (Sound#getKey kullanımdan kalkıyor).
     * Çakışmada ilk kayıt kazanır. Kurulamazsa boş harita döner (yedek dönüşüm devreye girer).
     */
    private static Map<String, String> legacySoundKeys() {
        Map<String, String> map = legacySoundKeys;
        if (map != null) return map;
        Map<String, String> built = new HashMap<>();
        try {
            Registry<Sound> registry = Registry.SOUNDS;
            for (Sound sound : registry) {
                NamespacedKey key = registry.getKey(sound);
                if (key == null) continue;
                String legacyName = key.getKey().toUpperCase(Locale.ROOT).replace('.', '_');
                built.putIfAbsent(legacyName, key.toString());
            }
        } catch (RuntimeException | LinkageError e) {
            debug("[SOUND] Sound registry scan failed: " + e.getMessage());
        }
        map = Collections.unmodifiableMap(built);
        legacySoundKeys = map;
        return map;
    }

    private static void debug(String message) {
        ReportSystemSpigot instance = ReportSystemSpigot.getInstance();
        if (instance != null) {
            instance.debug(message);
        }
    }
}
