package com.reportsystem.spigot.recording;

import com.reportsystem.common.database.ReplayDAO;
import com.reportsystem.common.models.Replay;
import com.reportsystem.common.replay.ReplaySerializer;
import com.reportsystem.common.replay.actions.ReplayAction;
import com.reportsystem.spigot.ReportSystemSpigot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * H-27: Aktif kayıtların çökme/kill'e karşı disk günlüğü (checkpoint).
 * <p>
 * Her aktif kayıt periyodik olarak anlık görüntü (snapshot) olarak
 * {@code <dataFolder>/recording-journal/<uuid>_<start>.bin} dosyasına yazılır
 * (geçici dosya + atomik taşıma). Meta bilgisi aynı yöntemle {@code .properties}
 * dosyasına yazılır. Kayıt normal yoldan DB'ye yazılınca dosyalar silinir;
 * çökmeden sonra açılışta {@link #recoverAsync(ReplayDAO, int)} kalan dosyaları
 * DB'ye taşır.
 * <p>
 * TÜM dosya G/Ç'si bu sınıfın tek iş parçacıklı executor'ında yapılır (ana thread
 * asla dosyaya dokunmaz); tek iş parçacığı aynı anahtar için yaz/sil sırasını korur.
 */
public class RecordingJournal {

    private static final String BIN_EXT = ".bin";
    private static final String META_EXT = ".properties";
    private static final String TMP_EXT = ".tmp";

    private final Logger logger;
    private final Path dir;
    private final Path corruptDir;
    private final ExecutorService executor;
    // Aynı kayıt için önceki checkpoint hâlâ kuyruktaysa yenisini ekleme (birikmeyi önler)
    private final Set<String> pendingCheckpoints = ConcurrentHashMap.newKeySet();
    // F-K1 "mezar taşı": DB'ye başarıyla yazılıp silinmesi istenen anahtarlar → silmeden sonra
    // kuyruğa düşen (zamanlayıcı yarışı) periyodik checkpoint dosyayı geri yaratmasın.
    // Değer = mezar taşı zamanı; 10 dk'dan eskiler budanır (anahtar UUID+başlangıç, tekrar kullanılmaz).
    private static final long TOMBSTONE_TTL_MS = 10L * 60L * 1000L;
    private final Map<String, Long> tombstones = new ConcurrentHashMap<>();

    public RecordingJournal(ReportSystemSpigot plugin) {
        this.logger = plugin.getLogger();
        this.dir = plugin.getDataFolder().toPath().resolve("recording-journal");
        this.corruptDir = dir.resolve("corrupt");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ReportSystem-RecordingJournal");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Dosya anahtarı: aynı oyuncunun art arda iki kaydı birbirinin dosyasını
     * silmesin diye UUID + başlangıç zamanı.
     */
    static String key(UUID recorded, long start) {
        return recorded.toString() + "_" + start;
    }

    /**
     * Anlık görüntüyü async olarak diske yazar. {@code snapshot} çağıranın kendi
     * kopyası olmalı (canlı liste değil).
     */
    public void checkpointAsync(UUID recorded, List<Integer> ids, String player, String world, long start,
            List<ReplayAction> snapshot) {
        checkpointAsync(recorded, ids, player, world, start, snapshot, false);
    }

    /**
     * @param force true → önceki checkpoint kuyruktayken de yaz (kayıt sonu, DB hatası
     *              sonrası son hâl). Tek iş parçacıklı executor sırayı korur, son yazılan kazanır.
     */
    public void checkpointAsync(UUID recorded, List<Integer> ids, String player, String world, long start,
            List<ReplayAction> snapshot, boolean force) {
        if (ids == null || ids.isEmpty() || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        String key = key(recorded, start);
        if (!force && tombstones.containsKey(key)) {
            return; // kayıt zaten DB'ye yazıldı ve günlüğü silindi
        }
        if (!force && !pendingCheckpoints.add(key)) {
            return; // önceki periyodik checkpoint henüz bitmedi
        }
        List<Integer> idsCopy = new ArrayList<>(ids);
        submit(() -> {
            try {
                // F-K1: silme ile bu iş arasında mezar taşı düştüyse yazma (executor içinde tekrar kontrol)
                if (!force && tombstones.containsKey(key)) {
                    return;
                }
                byte[] data = ReplaySerializer.serialize(snapshot, true);

                Properties meta = new Properties();
                meta.setProperty("ids", joinIds(idsCopy));
                meta.setProperty("player", player != null ? player : "");
                meta.setProperty("world", world != null ? world : "world");
                meta.setProperty("start", Long.toString(start));
                meta.setProperty("uuid", recorded.toString());
                java.io.ByteArrayOutputStream metaBytes = new java.io.ByteArrayOutputStream();
                meta.store(metaBytes, "ReportSystem recording journal");

                // F-K2: önce meta, sonra veri. Arada çökerse kurtarma "meta var, veri yok" durumunu
                // sessizce temizler; veri yazılmışsa meta da mutlaka vardır (yetim .bin oluşmaz).
                Files.createDirectories(dir);
                writeAtomic(dir.resolve(key + META_EXT), metaBytes.toByteArray());
                writeAtomic(dir.resolve(key + BIN_EXT), data);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[RECORDING-JOURNAL] Checkpoint yazılamadı (" + key + "): " + e.getMessage());
            } finally {
                if (!force) {
                    pendingCheckpoints.remove(key);
                }
            }
        }, () -> {
            if (!force) {
                pendingCheckpoints.remove(key);
            }
        });
    }

    /**
     * Kaydın günlük dosyalarını async siler (kayıt DB'ye başarıyla yazıldıktan sonra).
     */
    public void delete(UUID recorded, long start) {
        String key = key(recorded, start);
        long now = System.currentTimeMillis();
        tombstones.values().removeIf(t -> now - t > TOMBSTONE_TTL_MS);
        tombstones.put(key, now);
        submit(() -> deleteFiles(key), null);
    }

    /**
     * Açılışta kalan günlükleri DB'ye taşır. Her rapor ID'si için önce DB'de replay
     * var mı bakılır (çift kayıt yok). Bozuk dosya {@code corrupt/} klasörüne taşınır;
     * DB hatasında dosyalar bırakılır (sonraki açılışta tekrar denenir).
     */
    public void recoverAsync(ReplayDAO dao, int maxFileMb) {
        if (dao == null) {
            return;
        }
        submit(() -> recover(dao, maxFileMb), null);
    }

    /**
     * F-K5: crash-recovery kapalıyken açılışta, önceki oturumlardan kalan günlük dosyası
     * varsa tek bir uyarı yazar (dosyalara dokunmaz).
     */
    public void warnIfLeftoverAsync() {
        submit(() -> {
            if (!Files.isDirectory(dir)) {
                return;
            }
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + META_EXT)) {
                for (Path ignored : stream) {
                    count++;
                }
            } catch (IOException e) {
                return;
            }
            if (count > 0) {
                logger.warning("[RECORDING-JOURNAL] replay.crash-recovery kapalı ama " + dir + " içinde " + count
                        + " kurtarılmamış kayıt günlüğü var. Kurtarmak için crash-recovery.enabled: true yapıp sunucuyu yeniden başlatın.");
            }
        }, null);
    }

    /**
     * Executor'ı kapatır; kuyruktaki işler için kısa süre bekler (onDisable).
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                logger.warning("[RECORDING-JOURNAL] Günlük işleri 3 sn içinde bitmedi; kalan dosyalar sonraki açılışta işlenir.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------------
    // İç işler (yalnız executor thread'inde çalışır)
    // ---------------------------------------------------------------------

    private void submit(Runnable task, Runnable onRejected) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            // Kapanış sonrası: dosya kalır, açılış kurtarması dedup ile halleder.
            if (onRejected != null) {
                onRejected.run();
            }
        }
    }

    private void recover(ReplayDAO dao, int maxFileMb) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> metas = new ArrayList<>();
        List<Path> others = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String name = p.getFileName().toString();
                if (name.endsWith(META_EXT)) {
                    metas.add(p);
                } else {
                    others.add(p);
                }
            }
        } catch (IOException e) {
            logger.warning("[RECORDING-JOURNAL] Günlük klasörü okunamadı: " + e.getMessage());
            return;
        }

        int recovered = 0;
        for (Path metaPath : metas) {
            String fileName = metaPath.getFileName().toString();
            String key = fileName.substring(0, fileName.length() - META_EXT.length());
            Path binPath = dir.resolve(key + BIN_EXT);
            try {
                if (!Files.isRegularFile(binPath)) {
                    // F-K2: meta önce yazılıyor → ilk checkpoint'in verisi yazılmadan çökülmüş
                    // (ya da silme yarıda kalmış). Kurtarılacak veri yok; sessizce temizle.
                    deleteFiles(key);
                    continue;
                }

                Properties meta = new Properties();
                try (InputStream in = Files.newInputStream(metaPath)) {
                    meta.load(in);
                }
                List<Integer> ids = parseIds(meta.getProperty("ids", ""));
                String player = meta.getProperty("player", "");
                String world = meta.getProperty("world", "world");
                long start = Long.parseLong(meta.getProperty("start", "0").trim());
                if (ids.isEmpty() || start <= 0) {
                    throw new IllegalStateException("eksik meta (ids/start)");
                }

                byte[] data = Files.readAllBytes(binPath);
                long end = Files.getLastModifiedTime(binPath).toMillis();
                // Doğrulama: ReplaySerializer.deserialize (E paketinin ObjectInputFilter'ı burada da geçerli)
                List<ReplayAction> actions = ReplaySerializer.deserialize(data, true);
                if (actions == null || actions.isEmpty()) {
                    throw new IllegalStateException("boş aksiyon listesi");
                }

                long limitBytes = maxFileMb > 0 ? maxFileMb * 1024L * 1024L : -1L;
                FitResult fitted = fitToSizeLimit(actions, data, limitBytes, logger, "kurtarma " + key);

                boolean allOk = true;
                for (int id : ids) {
                    try {
                        if (dao.getReplayByReportId(id).isPresent()) {
                            continue; // zaten kayıtlı (normal yoldan yazılmış) → tekrar yazma
                        }
                        Replay replay = new Replay(id, player, world, start, end, fitted.actionCount(),
                                fitted.data(), true);
                        // F-K4: oluşturma zamanı = kurtarma anı (eski kayıt otomatik silmeye hemen takılmasın)
                        replay.setCreatedAt(System.currentTimeMillis());
                        dao.saveReplay(replay);
                        recovered++;
                        logger.info("[RECORDING-JOURNAL] Yarım kalan kayıt kurtarıldı: Rapor #" + id + " (" + player
                                + ", " + fitted.actionCount() + " aksiyon)");
                    } catch (Exception e) {
                        // F-K3: DB/sürücü/çalışma zamanı hatası → veri sağlam, dosya BIRAKILIR (corrupt'a taşınmaz)
                        allOk = false;
                        logger.warning("[RECORDING-JOURNAL] Rapor #" + id + " kurtarılamadı: " + e
                                + " — günlük dosyası bırakıldı, sonraki açılışta tekrar denenecek.");
                    }
                }
                if (allOk) {
                    deleteFiles(key);
                }
            } catch (Exception e) {
                // Okuma/çözme/meta hatası → bozuk dosya; kanıt silinmez, ayrı klasöre taşınır
                logger.warning("[RECORDING-JOURNAL] Bozuk günlük " + key + " ayrıldı (corrupt/): " + e);
                moveToCorrupt(metaPath);
                moveToCorrupt(binPath);
            }
        }

        // Yetim dosyalar: yarım kalmış .tmp silinir; meta'sız .bin bozuk sayılır
        for (Path p : others) {
            String name = p.getFileName().toString();
            try {
                if (name.endsWith(TMP_EXT)) {
                    Files.deleteIfExists(p);
                } else if (name.endsWith(BIN_EXT)) {
                    String key = name.substring(0, name.length() - BIN_EXT.length());
                    if (!Files.exists(dir.resolve(key + META_EXT))) {
                        moveToCorrupt(p);
                    }
                }
            } catch (IOException e) {
                logger.warning("[RECORDING-JOURNAL] Yetim dosya temizlenemedi " + name + ": " + e.getMessage());
            }
        }

        if (recovered > 0) {
            logger.info("[RECORDING-JOURNAL] Çökme kurtarması tamamlandı: " + recovered + " replay DB'ye yazıldı.");
        }
    }

    private void deleteFiles(String key) {
        for (String ext : new String[] { BIN_EXT, META_EXT, BIN_EXT + TMP_EXT, META_EXT + TMP_EXT }) {
            try {
                Files.deleteIfExists(dir.resolve(key + ext));
            } catch (IOException e) {
                logger.warning("[RECORDING-JOURNAL] Günlük dosyası silinemedi " + key + ext + ": " + e.getMessage());
            }
        }
    }

    private void moveToCorrupt(Path file) {
        try {
            if (!Files.exists(file)) {
                return;
            }
            Files.createDirectories(corruptDir);
            Files.move(file, corruptDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("[RECORDING-JOURNAL] Bozuk dosya taşınamadı " + file.getFileName() + ": " + e.getMessage());
        }
    }

    /**
     * Geçici dosyaya yaz + diske zorla (fsync) + atomik taşı.
     */
    private static void writeAtomic(Path target, byte[] data) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName().toString() + TMP_EXT);
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buf = ByteBuffer.wrap(data);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
            ch.force(true);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String joinIds(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static List<Integer> parseIds(String raw) {
        List<Integer> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            int id = Integer.parseInt(s);
            if (id > 0 && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------------
    // Boyut sınırı (RecordingManager.saveReplayAsync ile ortak)
    // ---------------------------------------------------------------------

    /** Serileştirilmiş veri + içindeki aksiyon sayısı. */
    record FitResult(byte[] data, int actionCount) {
    }

    /**
     * H-07: Veri {@code limitBytes}'ı aşıyorsa replay ATILMAZ; aksiyon listesi
     * kuyruktan kısaltılıp yeniden serileştirilir (en çok 3 deneme). 3 denemede
     * de sığmazsa son (en küçük) hâli yine döner — kanıt kaybı yerine büyük kayıt.
     * {@code limitBytes <= 0} → sınırsız.
     */
    static FitResult fitToSizeLimit(List<ReplayAction> actions, byte[] data, long limitBytes, Logger logger,
            String label) throws IOException {
        if (limitBytes <= 0 || data.length <= limitBytes) {
            return new FitResult(data, actions.size());
        }
        int original = actions.size();
        long originalBytes = data.length;
        int count = original;
        byte[] current = data;
        for (int attempt = 0; attempt < 3 && current.length > limitBytes && count > 1; attempt++) {
            int keep = (int) Math.max(1L, (long) (count * ((double) limitBytes / current.length) * 0.9));
            if (keep >= count) {
                keep = count - 1;
            }
            count = keep;
            current = ReplaySerializer.serialize(new ArrayList<>(actions.subList(0, count)), true);
        }
        logger.warning("[RECORDING] Replay boyut sınırını aştı, kuyruktan kısaltıldı (" + label + "): "
                + original + " → " + count + " aksiyon, "
                + String.format(java.util.Locale.ROOT, "%.2f MB → %.2f MB", originalBytes / 1048576.0,
                        current.length / 1048576.0)
                + (current.length > limitBytes ? " — hâlâ sınırın üstünde, yine de kaydediliyor." : ""));
        return new FitResult(current, count);
    }
}
