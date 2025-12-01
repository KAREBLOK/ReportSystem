package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.ItemAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ItemListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    // Yakın zamanda spawn edilen itemleri track et (중복 방지)
    private final Map<UUID, Long> recentItemSpawns = new HashMap<>();
    private static final long SPAWN_COOLDOWN = 100; // 100ms

    public ItemListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack item = event.getItemDrop().getItemStack();
            String itemData = ItemSerializer.serializeItemStack(item);

            session.addAction(new ItemAction(
                    ItemAction.ItemActionType.DROP,
                    itemData,
                    item.getAmount(),
                    event.getItemDrop().getLocation().getX(),
                    event.getItemDrop().getLocation().getY(),
                    event.getItemDrop().getLocation().getZ()
            ));

            plugin.getLogger().info("[RECORDING-DEBUG] Item dropped: " + item.getType().name() + " x" + item.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack item = event.getItem().getItemStack();
            String itemData = ItemSerializer.serializeItemStack(item);

            session.addAction(new ItemAction(
                    ItemAction.ItemActionType.PICKUP,
                    itemData,
                    item.getAmount(),
                    event.getItem().getLocation().getX(),
                    event.getItem().getLocation().getY(),
                    event.getItem().getLocation().getZ()
            ));

            plugin.getLogger().info("[RECORDING-DEBUG] Item picked up: " + item.getType().name() + " x" + item.getAmount());
        }
    }

    /**
     * ItemSpawnEvent - Tüm item spawnları için (blok kırma, koyun kırpma, vb.)
     * Bu event SADECE yakındaki kaydedilen oyuncular için tetiklenir
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.isCancelled()) return;

        Item itemEntity = event.getEntity();
        Location itemLoc = itemEntity.getLocation();

        // Duplicate spawn kontrolü
        UUID itemUUID = itemEntity.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (recentItemSpawns.containsKey(itemUUID)) {
            long lastSpawn = recentItemSpawns.get(itemUUID);
            if (currentTime - lastSpawn < SPAWN_COOLDOWN) {
                return; // Çok yakın zamanda kaydedildi, skip
            }
        }

        // Yakındaki kaydedilen oyuncuları bul (10 blok yarıçap)
        List<Player> nearbyRecordedPlayers = new ArrayList<>();
        for (Player player : itemLoc.getWorld().getNearbyEntities(itemLoc, 10, 10, 10,
                entity -> entity instanceof Player).stream()
                .map(entity -> (Player) entity)
                .toArray(Player[]::new)) {

            RecordingSession session = recordingManager.getSession(player.getUniqueId());
            if (session != null) {
                nearbyRecordedPlayers.add(player);
            }
        }

        // Eğer yakında kaydedilen oyuncu varsa, item spawn'ı kaydet
        if (!nearbyRecordedPlayers.isEmpty()) {
            ItemStack item = itemEntity.getItemStack();
            String itemData = ItemSerializer.serializeItemStack(item);

            // En yakın oyuncuyu bul
            Player closestPlayer = nearbyRecordedPlayers.stream()
                    .min(Comparator.comparingDouble(p -> p.getLocation().distance(itemLoc)))
                    .orElse(nearbyRecordedPlayers.get(0));

            RecordingSession session = recordingManager.getSession(closestPlayer.getUniqueId());

            if (session != null) {
                session.addAction(new ItemAction(
                        ItemAction.ItemActionType.DROP,
                        itemData,
                        item.getAmount(),
                        itemLoc.getX(),
                        itemLoc.getY(),
                        itemLoc.getZ()
                ));

                plugin.getLogger().info("[RECORDING-DEBUG] Item spawned (auto): " +
                        item.getType().name() + " x" + item.getAmount() +
                        " near " + closestPlayer.getName());

                // Spawn zamanını kaydet
                recentItemSpawns.put(itemUUID, currentTime);

                // 5 saniye sonra cache'den kaldır
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    recentItemSpawns.remove(itemUUID);
                }, 100L);
            }
        }
    }
}
