package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.BlockAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Su/lav akışı ve sünger emme gibi blok fizik olaylarını kaydeder.
 * Event-driven kayıt + periyodik alan taraması ile güvenilir sonuç sağlar.
 */
public class BlockPhysicsListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    private static final int PHYSICS_RADIUS = 100;
    private static final int SCAN_HORIZONTAL_RADIUS = 15;
    private static final int SCAN_DOWN = 60;
    private static final int SCAN_UP = 5;
    private static final int SPONGE_SCAN_RADIUS = 20;

    // Aktif akışkan tarama görevi
    private BukkitRunnable activeFluidScan = null;
    // Takip edilen akışkan blokları: packed long key -> Material ordinal
    // Key format: x (21 bit) | y (21 bit) | z (21 bit) → GC-free, String yok
    private final Map<Long, Material> trackedFluidBlocks = new ConcurrentHashMap<>();

    public BlockPhysicsListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    /** Koordinatları tek bir long değerine paketler (GC-free key) */
    private static long packKey(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    /**
     * Su/lav akışını yakalar ve periyodik alan taraması başlatır
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (recordingManager.getActiveRecordingSessions().isEmpty()) return;

        Block fromBlock = event.getBlock();
        Material fromType = fromBlock.getType();
        if (fromType != Material.WATER && fromType != Material.LAVA) return;

        // Yakında aktif kayıt oturumu var mı kontrol et
        Location toLoc = event.getToBlock().getLocation();
        if (!isNearAnyRecordingPlayer(toLoc)) return;

        // 1 tick sonra gerçek sonucu kontrol et
        // Su+lav birleşince COBBLESTONE/OBSIDIAN/STONE oluşur, anında kayıt yanlış olur
        Location toLocClone = toLoc.clone();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Material resultType = toLocClone.getBlock().getType();
            if (resultType != Material.AIR && resultType != Material.CAVE_AIR) {
                addPhysicsActionToNearbySessions(toLocClone, resultType.name());
            }
        }, 1L);

        // Periyodik alan taraması başlat (henüz çalışmıyorsa)
        startFluidScan(fromBlock.getLocation());
    }

    /**
     * Su+lav birleşimi sonucu oluşan blokları yakalar (obsidyen, cobblestone, stone).
     * BlockFromToEvent iptal edildiğinde bile bu event ateşlenir.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (recordingManager.getActiveRecordingSessions().isEmpty()) return;

        Material newType = event.getNewState().getType();

        // Sadece su+lav birleşimi sonuçlarını kaydet
        if (newType != Material.OBSIDIAN
                && newType != Material.COBBLESTONE
                && newType != Material.STONE) {
            return;
        }

        Location loc = event.getBlock().getLocation();
        if (!isNearAnyRecordingPlayer(loc)) return;

        // 1 tick sonra kaydet (event henüz uygulanmadı)
        Location locClone = loc.clone();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Material resultType = locClone.getBlock().getType();
            addPhysicsActionToNearbySessions(locClone, resultType.name());
        }, 1L);
    }

    /**
     * Sünger su emme olayını yakalar.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (recordingManager.getActiveRecordingSessions().isEmpty()) return;

        Location spongeLoc = event.getBlock().getLocation();
        World world = spongeLoc.getWorld();

        // 1) Doğrudan emilen blokları kaydet
        for (BlockState state : event.getBlocks()) {
            addPhysicsActionToNearbySessions(state.getLocation(), Material.AIR.name());
        }

        // 2) Geniş alanda su bloklarını snapshot al
        Set<Location> waterSnapshot = new HashSet<>();
        for (int x = -SPONGE_SCAN_RADIUS; x <= SPONGE_SCAN_RADIUS; x++) {
            for (int y = -SPONGE_SCAN_RADIUS; y <= SPONGE_SCAN_RADIUS; y++) {
                for (int z = -SPONGE_SCAN_RADIUS; z <= SPONGE_SCAN_RADIUS; z++) {
                    Block b = world.getBlockAt(
                            spongeLoc.getBlockX() + x,
                            spongeLoc.getBlockY() + y,
                            spongeLoc.getBlockZ() + z
                    );
                    if (b.getType() == Material.WATER || b.getType() == Material.LAVA) {
                        waterSnapshot.add(b.getLocation().clone());
                    }
                }
            }
        }

        // 3) Tekrarlayan kontrol: her 5 tick'te, 3 saniye boyunca
        if (!waterSnapshot.isEmpty()) {
            new BukkitRunnable() {
                int checksRemaining = 12;

                @Override
                public void run() {
                    if (checksRemaining <= 0 || recordingManager.getActiveRecordingSessions().isEmpty()) {
                        this.cancel();
                        return;
                    }
                    checksRemaining--;

                    Iterator<Location> it = waterSnapshot.iterator();
                    while (it.hasNext()) {
                        Location loc = it.next();
                        Material current = loc.getBlock().getType();
                        if (current != Material.WATER && current != Material.LAVA) {
                            addPhysicsActionToNearbySessions(loc, current.name());
                            it.remove();
                        }
                    }

                    if (waterSnapshot.isEmpty()) {
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 5L, 5L);
        }
    }

    /**
     * Periyodik akışkan taraması başlatır.
     * Değişiklik yoksa otomatik durur (erken çıkış).
     */
    private void startFluidScan(Location eventLocation) {
        if (activeFluidScan != null) return;

        World world = eventLocation.getWorld();
        int centerX = eventLocation.getBlockX();
        int centerY = eventLocation.getBlockY();
        int centerZ = eventLocation.getBlockZ();

        // İlk snapshot
        snapshotFluidBlocks(world, centerX, centerY, centerZ);

        activeFluidScan = new BukkitRunnable() {
            int noChangeCount = 0; // Üst üste değişiklik olmayan tarama sayısı

            @Override
            public void run() {
                if (recordingManager.getActiveRecordingSessions().isEmpty()) {
                    cleanup();
                    return;
                }

                int changes = scanForFluidChanges(world, centerX, centerY, centerZ);

                if (changes == 0) {
                    noChangeCount++;
                    // 3 üst üste değişiklik yoksa dur (1.5 saniye sabit = akış tamamlandı)
                    if (noChangeCount >= 3) {
                        cleanup();
                    }
                } else {
                    noChangeCount = 0; // Değişiklik var, sayacı sıfırla
                }
            }

            private void cleanup() {
                this.cancel();
                activeFluidScan = null;
                trackedFluidBlocks.clear();
            }
        };

        activeFluidScan.runTaskTimer(plugin, 10L, 10L);
    }

    /**
     * Alandaki akışkan blokların ilk snapshot'ını alır
     */
    private void snapshotFluidBlocks(World world, int cx, int cy, int cz) {
        trackedFluidBlocks.clear();

        for (int x = -SCAN_HORIZONTAL_RADIUS; x <= SCAN_HORIZONTAL_RADIUS; x++) {
            for (int y = -SCAN_DOWN; y <= SCAN_UP; y++) {
                for (int z = -SCAN_HORIZONTAL_RADIUS; z <= SCAN_HORIZONTAL_RADIUS; z++) {
                    int bx = cx + x;
                    int by = cy + y;
                    int bz = cz + z;

                    Material type = world.getBlockAt(bx, by, bz).getType();
                    if (type == Material.WATER || type == Material.LAVA) {
                        trackedFluidBlocks.put(packKey(bx, by, bz), type);
                    }
                }
            }
        }
    }

    /**
     * Alanı tarar, değişiklikleri kaydeder. Değişiklik sayısını döndürür.
     */
    private int scanForFluidChanges(World world, int cx, int cy, int cz) {
        int changes = 0;

        for (int x = -SCAN_HORIZONTAL_RADIUS; x <= SCAN_HORIZONTAL_RADIUS; x++) {
            for (int y = -SCAN_DOWN; y <= SCAN_UP; y++) {
                for (int z = -SCAN_HORIZONTAL_RADIUS; z <= SCAN_HORIZONTAL_RADIUS; z++) {
                    int bx = cx + x;
                    int by = cy + y;
                    int bz = cz + z;
                    long key = packKey(bx, by, bz);

                    Material currentType = world.getBlockAt(bx, by, bz).getType();
                    boolean isFluid = (currentType == Material.WATER || currentType == Material.LAVA);
                    boolean wasTracked = trackedFluidBlocks.containsKey(key);

                    if (isFluid && !wasTracked) {
                        addPhysicsActionToNearbySessions(
                                new Location(world, bx, by, bz), currentType.name());
                        trackedFluidBlocks.put(key, currentType);
                        changes++;
                    } else if (!isFluid && wasTracked) {
                        // Gerçek sonucu kaydet (OBSIDIAN, COBBLESTONE olabilir, sadece AIR değil)
                        addPhysicsActionToNearbySessions(
                                new Location(world, bx, by, bz), currentType.name());
                        trackedFluidBlocks.remove(key);
                        changes++;
                    }
                }
            }
        }

        return changes;
    }

    /**
     * Verilen konumun yakınında aktif kayıt oturumu olan oyuncu var mı kontrol eder.
     * Bu sayede uzaktaki su/lav olayları için gereksiz tarama başlatılmaz.
     */
    private boolean isNearAnyRecordingPlayer(Location loc) {
        for (UUID uuid : recordingManager.getActiveRecordingSessions().keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (!player.getWorld().equals(loc.getWorld())) continue;
            if (player.getLocation().distanceSquared(loc) <= PHYSICS_RADIUS * PHYSICS_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fizik olayını yakındaki tüm aktif kayıt oturumlarına ekler
     */
    private void addPhysicsActionToNearbySessions(Location blockLoc, String blockType) {
        Map<UUID, RecordingSession> sessions = recordingManager.getActiveRecordingSessions();
        if (sessions.isEmpty()) return;

        for (Map.Entry<UUID, RecordingSession> entry : sessions.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            if (!player.getWorld().equals(blockLoc.getWorld())) continue;
            if (player.getLocation().distanceSquared(blockLoc) > PHYSICS_RADIUS * PHYSICS_RADIUS) continue;

            entry.getValue().addAction(new BlockAction(
                    BlockAction.BlockActionType.PLACE_BLOCK,
                    blockLoc.getBlockX(),
                    blockLoc.getBlockY(),
                    blockLoc.getBlockZ(),
                    blockType
            ));
        }
    }
}
