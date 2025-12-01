package com.reportsystem.spigot.recording;

import com.reportsystem.common.replay.actions.AnimationAction;
import com.reportsystem.common.replay.actions.EntityStateAction;
import com.reportsystem.common.replay.actions.EquipmentAction;
import com.reportsystem.common.replay.actions.NearbyPlayerAction;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.utils.ItemSerializer;
import com.reportsystem.spigot.utils.SkinUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periyodik olarak nearby player'ları takip eder ve hareketlerini kaydeder
 * Report edilen oyuncu hareketsiz olsa bile çalışır
 */
public class NearbyPlayerTracker {

    private final ReportSystemSpigot plugin;
    private final RecordingSession session;
    private final Player recordedPlayer;
    private BukkitTask trackingTask;

    // Config değerleri
    private final boolean enabled;
    private final int intervalTicks;
    private final double trackingDistance;
    private final double movementThreshold;
    private final boolean trackEquipment;
    private final boolean trackAnimations;
    private final boolean trackSneaking;
    private final boolean trackSprinting;

    // Tracking state
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Map<EquipmentAction.EquipmentSlot, ItemStack>> lastEquipment = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastSneakingState = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastSprintingState = new ConcurrentHashMap<>();
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();

    public NearbyPlayerTracker(ReportSystemSpigot plugin, RecordingSession session, Player recordedPlayer) {
        this.plugin = plugin;
        this.session = session;
        this.recordedPlayer = recordedPlayer;

        // Config'den ayarları yükle
        this.enabled = plugin.getConfig().getBoolean("replay.nearby-player-tracking.enabled", true);
        this.intervalTicks = plugin.getConfig().getInt("replay.nearby-player-tracking.interval-ticks", 5);
        this.trackingDistance = plugin.getConfig().getDouble("replay.nearby-player-tracking.distance", 48.0);
        this.movementThreshold = plugin.getConfig().getDouble("replay.nearby-player-tracking.movement-threshold", 0.05);
        this.trackEquipment = plugin.getConfig().getBoolean("replay.nearby-player-tracking.track.equipment", true);
        this.trackAnimations = plugin.getConfig().getBoolean("replay.nearby-player-tracking.track.animations", true);
        this.trackSneaking = plugin.getConfig().getBoolean("replay.nearby-player-tracking.track.sneaking", true);
        this.trackSprinting = plugin.getConfig().getBoolean("replay.nearby-player-tracking.track.sprinting", true);

        plugin.getLogger().info("[NEARBY-TRACKER] Initialized for " + recordedPlayer.getName() +
                " | Interval: " + intervalTicks + " ticks" +
                " | Distance: " + trackingDistance + " blocks" +
                " | Threshold: " + movementThreshold);
    }

    /**
     * Tracking'i başlatır
     */
    public void start() {
        if (!enabled) {
            plugin.getLogger().info("[NEARBY-TRACKER] Disabled in config, not starting");
            return;
        }

        trackingTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::trackNearbyPlayers,
                20L, // İlk çalışma (1 saniye sonra)
                intervalTicks // Her X tick'te bir
        );

        plugin.getLogger().info("[NEARBY-TRACKER] Started for " + recordedPlayer.getName());
    }

    /**
     * Tracking'i durdurur
     */
    public void stop() {
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }

        // Tüm tracked player'ları DISAPPEAR yap
        for (UUID uuid : trackedPlayers) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                recordPlayerDisappear(player);
            }
        }

        // Temizlik
        lastLocations.clear();
        lastEquipment.clear();
        lastSneakingState.clear();
        lastSprintingState.clear();
        trackedPlayers.clear();

        plugin.getLogger().info("[NEARBY-TRACKER] Stopped for " + recordedPlayer.getName());
    }

    /**
     * Ana tracking metodu - her interval'de çalışır
     */
    private void trackNearbyPlayers() {
        if (!recordedPlayer.isOnline() || !recordedPlayer.isValid()) {
            stop();
            return;
        }

        Location recordedLoc = recordedPlayer.getLocation();
        Set<UUID> currentNearbyPlayers = new HashSet<>();

        // Aynı world'deki tüm oyuncuları kontrol et
        for (Player nearbyPlayer : recordedPlayer.getWorld().getPlayers()) {
            // Kendisini atla
            if (nearbyPlayer.equals(recordedPlayer)) {
                continue;
            }

            // Görünürlük kontrolü
            if (!recordedPlayer.canSee(nearbyPlayer)) {
                continue;
            }

            // Mesafe kontrolü (squared distance for performance)
            double distanceSquared = nearbyPlayer.getLocation().distanceSquared(recordedLoc);
            double maxDistanceSquared = trackingDistance * trackingDistance;

            if (distanceSquared <= maxDistanceSquared) {
                currentNearbyPlayers.add(nearbyPlayer.getUniqueId());

                // Yeni oyuncu mu?
                if (!trackedPlayers.contains(nearbyPlayer.getUniqueId())) {
                    recordPlayerAppear(nearbyPlayer);
                } else {
                    // Var olan oyuncu - değişiklikleri kontrol et
                    checkPlayerChanges(nearbyPlayer);
                }
            }
        }

        // Artık menzilde olmayan oyuncuları kaldır
        Set<UUID> playersToRemove = new HashSet<>(trackedPlayers);
        playersToRemove.removeAll(currentNearbyPlayers);

        for (UUID uuid : playersToRemove) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                recordPlayerDisappear(player);
            }
            trackedPlayers.remove(uuid);
        }
    }

    /**
     * Oyuncu ilk kez göründüğünde kaydeder
     */
    private void recordPlayerAppear(Player player) {
        trackedPlayers.add(player.getUniqueId());

        // Location kaydet
        lastLocations.put(player.getUniqueId(), player.getLocation().clone());

        // Skin bilgisi
        String[] skinData = SkinUtils.getSkinData(player);
        String skinTexture = skinData[0];
        String skinSignature = skinData[1];

        // Equipment bilgisi
        Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipment = null;
        if (trackEquipment) {
            equipment = getPlayerEquipment(player);
            // Equipment state'i kaydet
            Map<EquipmentAction.EquipmentSlot, ItemStack> equipmentState = new HashMap<>();
            equipmentState.put(EquipmentAction.EquipmentSlot.MAIN_HAND, player.getInventory().getItemInMainHand());
            equipmentState.put(EquipmentAction.EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand());
            equipmentState.put(EquipmentAction.EquipmentSlot.HELMET, player.getInventory().getHelmet());
            equipmentState.put(EquipmentAction.EquipmentSlot.CHESTPLATE, player.getInventory().getChestplate());
            equipmentState.put(EquipmentAction.EquipmentSlot.LEGGINGS, player.getInventory().getLeggings());
            equipmentState.put(EquipmentAction.EquipmentSlot.BOOTS, player.getInventory().getBoots());
            lastEquipment.put(player.getUniqueId(), equipmentState);
        }

        // Sneaking/Sprinting state kaydet
        if (trackSneaking) {
            lastSneakingState.put(player.getUniqueId(), player.isSneaking());
        }
        if (trackSprinting) {
            lastSprintingState.put(player.getUniqueId(), player.isSprinting());
        }

        // Full inventory kaydet
        Map<Integer, EquipmentAction.ItemData> inventory = getPlayerFullInventory(player);

        // PLAYER_APPEAR action
        NearbyPlayerAction appearAction = new NearbyPlayerAction(
                NearbyPlayerAction.ActionType.PLAYER_APPEAR,
                player.getUniqueId(),
                player.getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch(),
                skinTexture,
                skinSignature,
                equipment,
                inventory // Full inventory
        );

        session.addAction(appearAction);

        plugin.getLogger().info("[NEARBY-TRACKER] Player appeared: " + player.getName() +
                " at " + String.format("%.1f, %.1f, %.1f", player.getLocation().getX(),
                player.getLocation().getY(), player.getLocation().getZ()));
    }

    /**
     * Oyuncu değişikliklerini kontrol eder (hareket, equipment, state)
     */
    private void checkPlayerChanges(Player player) {
        UUID uuid = player.getUniqueId();
        boolean hasChanges = false;

        // 1. Hareket kontrolü
        Location lastLoc = lastLocations.get(uuid);
        Location currentLoc = player.getLocation();

        if (lastLoc == null || lastLoc.distanceSquared(currentLoc) > (movementThreshold * movementThreshold)) {
            recordPlayerMove(player);
            lastLocations.put(uuid, currentLoc.clone());
            hasChanges = true;
        }

        // 2. Equipment kontrolü
        if (trackEquipment) {
            Map<EquipmentAction.EquipmentSlot, ItemStack> lastEq = lastEquipment.get(uuid);
            if (lastEq != null && checkEquipmentChanges(player, lastEq)) {
                hasChanges = true;
            }
        }

        // 3. Sneaking state kontrolü
        if (trackSneaking) {
            Boolean lastSneak = lastSneakingState.get(uuid);
            boolean currentSneak = player.isSneaking();
            if (lastSneak == null || lastSneak != currentSneak) {
                recordSneakingState(player, currentSneak);
                lastSneakingState.put(uuid, currentSneak);
                hasChanges = true;
            }
        }

        // 4. Sprinting state kontrolü
        if (trackSprinting) {
            Boolean lastSprint = lastSprintingState.get(uuid);
            boolean currentSprint = player.isSprinting();
            if (lastSprint == null || lastSprint != currentSprint) {
                recordSprintingState(player, currentSprint);
                lastSprintingState.put(uuid, currentSprint);
                hasChanges = true;
            }
        }
    }

    /**
     * Oyuncu hareketini kaydeder
     */
    private void recordPlayerMove(Player player) {
        NearbyPlayerAction moveAction = new NearbyPlayerAction(
                NearbyPlayerAction.ActionType.PLAYER_MOVE,
                player.getUniqueId(),
                player.getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch(),
                "", ""
        );

        session.addAction(moveAction);
    }

    /**
     * Oyuncu kaybolduğunda kaydeder
     */
    private void recordPlayerDisappear(Player player) {
        NearbyPlayerAction disappearAction = new NearbyPlayerAction(
                NearbyPlayerAction.ActionType.PLAYER_DISAPPEAR,
                player.getUniqueId(),
                player.getName(),
                0, 0, 0, 0, 0, "", ""
        );

        session.addAction(disappearAction);

        // State'leri temizle
        lastLocations.remove(player.getUniqueId());
        lastEquipment.remove(player.getUniqueId());
        lastSneakingState.remove(player.getUniqueId());
        lastSprintingState.remove(player.getUniqueId());

        plugin.getLogger().info("[NEARBY-TRACKER] Player disappeared: " + player.getName());
    }

    /**
     * Equipment değişikliklerini kontrol eder
     */
    private boolean checkEquipmentChanges(Player player, Map<EquipmentAction.EquipmentSlot, ItemStack> lastEq) {
        boolean hasChanges = false;

        // Her slot'u kontrol et
        ItemStack[] currentItems = {
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(),
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };

        EquipmentAction.EquipmentSlot[] slots = {
                EquipmentAction.EquipmentSlot.MAIN_HAND,
                EquipmentAction.EquipmentSlot.OFF_HAND,
                EquipmentAction.EquipmentSlot.HELMET,
                EquipmentAction.EquipmentSlot.CHESTPLATE,
                EquipmentAction.EquipmentSlot.LEGGINGS,
                EquipmentAction.EquipmentSlot.BOOTS
        };

        for (int i = 0; i < slots.length; i++) {
            EquipmentAction.EquipmentSlot slot = slots[i];
            ItemStack currentItem = currentItems[i];
            ItemStack lastItem = lastEq.get(slot);

            if (!itemsEqual(currentItem, lastItem)) {
                // Equipment değişti
                recordEquipmentChange(player, slot, currentItem);
                lastEq.put(slot, currentItem != null ? currentItem.clone() : null);
                hasChanges = true;
            }
        }

        return hasChanges;
    }

    /**
     * Equipment değişikliğini kaydeder
     */
    private void recordEquipmentChange(Player player, EquipmentAction.EquipmentSlot slot, ItemStack item) {
        EquipmentAction.ItemData itemData = convertToItemData(item);
        EquipmentAction equipAction = new EquipmentAction(slot, itemData);

        session.addAction(equipAction);

        plugin.getLogger().info("[NEARBY-TRACKER] Equipment changed for " + player.getName() +
                " | Slot: " + slot + " | Item: " + (item != null ? item.getType() : "AIR"));
    }

    /**
     * Sneaking state'i kaydeder
     */
    private void recordSneakingState(Player player, boolean sneaking) {
        EntityStateAction stateAction = new EntityStateAction(
                EntityStateAction.StateType.SNEAKING,
                sneaking,
                player.getUniqueId() // Nearby player UUID
        );
        session.addAction(stateAction);

        plugin.getLogger().info("[NEARBY-TRACKER] Sneaking state for " + player.getName() + ": " + sneaking);
    }

    /**
     * Sprinting state'i kaydeder
     */
    private void recordSprintingState(Player player, boolean sprinting) {
        EntityStateAction stateAction = new EntityStateAction(
                EntityStateAction.StateType.SPRINTING,
                sprinting,
                player.getUniqueId() // Nearby player UUID
        );
        session.addAction(stateAction);

        plugin.getLogger().info("[NEARBY-TRACKER] Sprinting state for " + player.getName() + ": " + sprinting);
    }

    /**
     * İki ItemStack'in eşit olup olmadığını kontrol eder
     */
    private boolean itemsEqual(ItemStack item1, ItemStack item2) {
        if (item1 == null && item2 == null) return true;
        if (item1 == null || item2 == null) return false;
        if (item1.getType() == Material.AIR && item2.getType() == Material.AIR) return true;
        if (item1.getType() == Material.AIR || item2.getType() == Material.AIR) return false;

        return item1.isSimilar(item2) && item1.getAmount() == item2.getAmount();
    }

    /**
     * Oyuncunun tüm equipment'ını alır
     */
    private Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> getPlayerEquipment(Player player) {
        Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipment = new HashMap<>();

        addEquipmentIfNotEmpty(equipment, EquipmentAction.EquipmentSlot.MAIN_HAND, player.getInventory().getItemInMainHand());
        addEquipmentIfNotEmpty(equipment, EquipmentAction.EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand());
        addEquipmentIfNotEmpty(equipment, EquipmentAction.EquipmentSlot.HELMET, player.getInventory().getHelmet());
        addEquipmentIfNotEmpty(equipment, EquipmentAction.EquipmentSlot.CHESTPLATE, player.getInventory().getChestplate());
        addEquipmentIfNotEmpty(equipment, EquipmentAction.EquipmentSlot.LEGGINGS, player.getInventory().getLeggings());
        addEquipmentIfNotEmpty(equipment, EquipmentAction.EquipmentSlot.BOOTS, player.getInventory().getBoots());

        return equipment;
    }

    /**
     * Equipment'a item ekler (boş değilse)
     */
    private void addEquipmentIfNotEmpty(Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipment,
                                        EquipmentAction.EquipmentSlot slot, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            equipment.put(slot, convertToItemData(item));
        }
    }

    /**
     * ItemStack'i ItemData'ya dönüştürür
     */
    private EquipmentAction.ItemData convertToItemData(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }

        String displayName = null;
        List<String> lore = null;
        int customModelData = 0;
        boolean unbreakable = false;

        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();

            if (meta.hasDisplayName()) {
                displayName = meta.getDisplayName();
            }

            if (meta.hasLore()) {
                lore = meta.getLore();
            }

            if (meta.hasCustomModelData()) {
                customModelData = meta.getCustomModelData();
            }

            unbreakable = meta.isUnbreakable();
        }

        // Enchantment'ları topla
        Map<String, Integer> enchantments = new HashMap<>();
        itemStack.getEnchantments().forEach((enchant, level) -> {
            enchantments.put(enchant.getKey().getKey(), level);
        });

        // ItemStack'i serialize et
        byte[] itemData = ItemSerializer.itemStackToBytes(itemStack);

        return new EquipmentAction.ItemData(
                itemStack.getType().name(),
                itemStack.getAmount(),
                itemStack.getDurability(),
                displayName,
                lore,
                enchantments,
                itemData,
                customModelData,
                unbreakable
        );
    }

    /**
     * Oyuncunun tam inventory'sini döndürür (41 slot)
     */
    private Map<Integer, EquipmentAction.ItemData> getPlayerFullInventory(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        Map<Integer, EquipmentAction.ItemData> inventoryMap = new HashMap<>();

        // Main inventory (0-35 slots)
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                inventoryMap.put(i, convertItemToData(item));
            }
        }

        // Armor slots (36-39)
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && armor[i].getType() != Material.AIR) {
                inventoryMap.put(36 + i, convertItemToData(armor[i]));
            }
        }

        // Offhand (slot 40)
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            inventoryMap.put(40, convertItemToData(offhand));
        }

        plugin.getLogger().info("[NEARBY-TRACKER] Full inventory recorded for " + player.getName() +
                ": " + inventoryMap.size() + " items");

        return inventoryMap;
    }

    /**
     * ItemStack'i EquipmentAction.ItemData'ya çevirir
     */
    private EquipmentAction.ItemData convertItemToData(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        try {
            // ItemStack'i serialize et
            byte[] itemStackData = ItemSerializer.itemStackToBytes(item);

            // Enchantment'ları al
            Map<String, Integer> enchantments = new HashMap<>();
            item.getEnchantments().forEach((enchant, level) -> {
                enchantments.put(enchant.getKey().getKey(), level);
            });

            // Meta bilgileri
            String displayName = null;
            List<String> lore = null;
            int customModelData = 0;
            boolean unbreakable = false;

            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();

                if (meta.hasDisplayName()) {
                    displayName = meta.getDisplayName();
                }

                if (meta.hasLore()) {
                    lore = meta.getLore();
                }

                if (meta.hasCustomModelData()) {
                    customModelData = meta.getCustomModelData();
                }

                unbreakable = meta.isUnbreakable();
            }

            return new EquipmentAction.ItemData(
                    item.getType().name(),
                    item.getAmount(),
                    item.getDurability(),
                    displayName,
                    lore,
                    enchantments,
                    itemStackData,
                    customModelData,
                    unbreakable
            );

        } catch (Exception e) {
            plugin.getLogger().warning("[NEARBY-TRACKER] Failed to convert item: " + e.getMessage());
            return null;
        }
    }

    // Getter'lar
    public boolean isEnabled() {
        return enabled;
    }

    public int getTrackedPlayerCount() {
        return trackedPlayers.size();
    }
}
