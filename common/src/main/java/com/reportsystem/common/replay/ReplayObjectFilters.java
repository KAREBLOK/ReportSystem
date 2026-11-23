package com.reportsystem.common.replay;

import java.io.ObjectInputFilter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replay verisi okunurken kullanılan izin listeli Java serileştirme filtreleri.
 *
 * <p>Replay blob'ları {@link java.io.ObjectInputStream} ile okunduğu için filtresiz okuma,
 * veritabanına yazılabilen herhangi bir sınıfın (gadget zinciri) çözülmesine izin verir.
 * Buradaki filtreler yalnız replay modelinin gerçekten kullandığı sınıflara izin verir,
 * geri kalan her şeyi ({@code !*}) reddeder ve boyut/derinlik sınırları koyar.</p>
 *
 * <p>Not: {@code java.util.*} yalnız {@code java.util} paketinin kendisini kapsar;
 * alt paketler ({@code java.util.concurrent} vb.) kasıtlı olarak reddedilir.</p>
 */
public final class ReplayObjectFilters {

    private static final Logger LOGGER = Logger.getLogger("ReportSystem");

    /** Reddedilen sınıf başına yalnız bir kez log yazmak için. */
    private static final Set<String> LOGGED_REJECTIONS = ConcurrentHashMap.newKeySet();

    /** Aşılan sınır türü (depth/refs/bytes/array) başına yalnız bir kez log yazmak için. */
    private static final Set<String> LOGGED_LIMITS = ConcurrentHashMap.newKeySet();

    private static final long MAX_REFS = 10_000_000L;
    private static final long MAX_BYTES = 536_870_912L;
    private static final long MAX_ARRAY = 10_000_000L;
    /** Replay aksiyon modeli sığ; 24 yeterli. */
    private static final long REPLAY_MAX_DEPTH = 24L;
    /** Bukkit ItemStack (iç içe meta: shulker içeriği, kitap, banner) daha derin olabilir. */
    private static final long BUKKIT_ITEM_MAX_DEPTH = 64L;

    private static String limits(long maxDepth) {
        return "maxdepth=" + maxDepth + ";maxrefs=" + MAX_REFS + ";maxbytes=" + MAX_BYTES
                + ";maxarray=" + MAX_ARRAY + ";";
    }

    /** Replay aksiyon listesi (ReplaySerializer) için desen. */
    private static final String REPLAY_PATTERN = limits(REPLAY_MAX_DEPTH)
            + "java.lang.*;java.util.*;com.reportsystem.common.replay.actions.*;com.google.common.collect.*;!*";

    /** BukkitObjectInputStream ile okunan ItemStack verisi (ItemSerializer) için desen. */
    private static final String BUKKIT_ITEM_PATTERN = limits(BUKKIT_ITEM_MAX_DEPTH)
            + "java.lang.*;java.util.*;com.google.common.collect.*;org.bukkit.util.io.*;!*";

    private static final ObjectInputFilter REPLAY =
            logging(ObjectInputFilter.Config.createFilter(REPLAY_PATTERN), REPLAY_MAX_DEPTH);

    private static final ObjectInputFilter BUKKIT_ITEM =
            logging(ObjectInputFilter.Config.createFilter(BUKKIT_ITEM_PATTERN), BUKKIT_ITEM_MAX_DEPTH);

    private ReplayObjectFilters() {
    }

    /**
     * Replay aksiyon listesi okunurken kullanılacak filtre.
     */
    public static ObjectInputFilter replay() {
        return REPLAY;
    }

    /**
     * Aksiyonların içindeki Bukkit ItemStack verisi okunurken kullanılacak filtre.
     */
    public static ObjectInputFilter bukkitItem() {
        return BUKKIT_ITEM;
    }

    /**
     * Temel filtreyi sarar. REJECTED sonucunda red nedenini ayırır:
     * sınır aşımı (depth/refs/bytes/array) → sınır türü başına bir kez "limit exceeded" satırı;
     * gerçek sınıf reddi → sınıf başına bir kez "rejected class" satırı.
     * Sınırlar createFilter'ın kuralıyla aynı karşılaştırmayla (değer &gt; sınır) yeniden kontrol edilir.
     */
    private static ObjectInputFilter logging(ObjectInputFilter delegate, long maxDepth) {
        return info -> {
            ObjectInputFilter.Status status = delegate.checkInput(info);
            if (status == ObjectInputFilter.Status.REJECTED) {
                String limit = exceededLimit(info, maxDepth);
                Class<?> clazz = info.serialClass();
                if (limit != null) {
                    if (LOGGED_LIMITS.add(limit)) {
                        LOGGER.log(Level.WARNING, "[REPLAY-FILTER] limit exceeded (" + limit + ")"
                                + (clazz != null ? " at class " + clazz.getName() : "")
                                + " (depth=" + info.depth()
                                + ", refs=" + info.references()
                                + ", bytes=" + info.streamBytes()
                                + ", arrayLength=" + info.arrayLength() + ")");
                    }
                } else if (clazz != null && LOGGED_REJECTIONS.add(clazz.getName())) {
                    LOGGER.log(Level.WARNING, "[REPLAY-FILTER] rejected class " + clazz.getName());
                }
            }
            return status;
        };
    }

    /**
     * Aşılan sınırın adı; hiçbiri aşılmadıysa null (red sınıf kuralından gelmiştir).
     */
    private static String exceededLimit(ObjectInputFilter.FilterInfo info, long maxDepth) {
        if (info.depth() > maxDepth) {
            return "depth";
        }
        if (info.references() > MAX_REFS) {
            return "refs";
        }
        if (info.streamBytes() > MAX_BYTES) {
            return "bytes";
        }
        if (info.arrayLength() > MAX_ARRAY) {
            return "array";
        }
        return null;
    }
}
