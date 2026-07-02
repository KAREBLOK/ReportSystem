package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.EquipmentAction;
import com.reportsystem.common.replay.actions.EquipmentAction.ItemData;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EquipmentListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public EquipmentListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    /**
     * ItemStack'i ItemData'ya dönüştürür
     */
    private ItemData convertToItemData(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Item is null or air");
            return null;
        }

        String displayName = null;
        List<String> lore = null;
        int customModelData = 0;
        boolean unbreakable = false;

        if (itemStack.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();

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

            // İksir meta kontrolü
            if (meta instanceof org.bukkit.inventory.meta.PotionMeta) {
                org.bukkit.inventory.meta.PotionMeta potionMeta = (org.bukkit.inventory.meta.PotionMeta) meta;
                ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Potion type detected: " + itemStack.getType().name());

                // İksir tipini lore'a ekle
                if (lore == null) {
                    lore = new ArrayList<>();
                }
                if (potionMeta.hasColor()) {
                    lore.add("Color: " + potionMeta.getColor().toString());
                }
            }
        }

        // Enchantment'ları topla
        Map<String, Integer> enchantments = new HashMap<>();
        itemStack.getEnchantments().forEach((enchant, level) -> {
            enchantments.put(enchant.getKey().getKey(), level);
        });

        // ItemStack'i serialize et
        byte[] itemData = ItemSerializer.itemStackToBytes(itemStack);

        ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Converting item: " + itemStack.getType().name() +
                " x" + itemStack.getAmount());

        return new ItemData(
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            int newSlot = event.getNewSlot();

            ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Item held change for " + player.getName() +
                    " - New slot: " + newSlot);

            // Bir tick bekle, çünkü item henüz değişmemiş olabilir
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = player.getInventory().getItem(newSlot);

                ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Item in slot " + newSlot + ": " +
                        (newItem != null ? newItem.getType().name() : "NULL"));

                ItemData itemData = convertToItemData(newItem);
                session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.MAIN_HAND, itemData));

                ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Recorded main hand change: " +
                        (itemData != null ? itemData.getMaterial() : "EMPTY"));
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Hand swap for " + player.getName());

            // Swap sonrası durumu kaydet - 2 tick bekle
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();

                ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] After swap - Main: " +
                        (mainHand != null ? mainHand.getType().name() : "NULL") +
                        ", Off: " + (offHand != null ? offHand.getType().name() : "NULL"));

                ItemData mainHandData = convertToItemData(mainHand);
                ItemData offHandData = convertToItemData(offHand);

                session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.MAIN_HAND, mainHandData));
                session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.OFF_HAND, offHandData));

                ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Recorded hand swap");
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getWhoClicked();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            // Envanter tıklaması - equipment değişmiş olabilir
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recordAllEquipment(player, session);
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Item pickup by " + player.getName());

            // Item alındıktan sonra equipment'ı güncelle
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recordAllEquipment(player, session);
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Item drop by " + player.getName());

            // Item atıldıktan sonra equipment'ı güncelle
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recordAllEquipment(player, session);
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack brokenItem = event.getBrokenItem();
            ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Item break by " + player.getName() +
                    " - Item: " + brokenItem.getType().name());

            // Item kırıldıktan sonra equipment'ı güncelle
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recordAllEquipment(player, session);
            }, 2L);
        }
    }

    /**
     * Tüm equipment'ı kaydeder
     */
    private void recordAllEquipment(Player player, RecordingSession session) {
        ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] Recording all equipment for " + player.getName());

        // Ana el
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemData mainHandData = convertToItemData(mainHand);
        session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.MAIN_HAND, mainHandData));

        // Yan el
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemData offHandData = convertToItemData(offHand);
        session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.OFF_HAND, offHandData));

        // Zırhlar
        ItemStack helmet = player.getInventory().getHelmet();
        ItemData helmetData = convertToItemData(helmet);
        session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.HELMET, helmetData));

        ItemStack chestplate = player.getInventory().getChestplate();
        ItemData chestplateData = convertToItemData(chestplate);
        session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.CHESTPLATE, chestplateData));

        ItemStack leggings = player.getInventory().getLeggings();
        ItemData leggingsData = convertToItemData(leggings);
        session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.LEGGINGS, leggingsData));

        ItemStack boots = player.getInventory().getBoots();
        ItemData bootsData = convertToItemData(boots);
        session.addAction(new EquipmentAction(EquipmentAction.EquipmentSlot.BOOTS, bootsData));

        ReportSystemSpigot.getInstance().debug("[EQUIPMENT-DEBUG] All equipment recorded");
    }
}