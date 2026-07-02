package com.reportsystem.common.replay.actions;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EquipmentAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum EquipmentSlot {
        MAIN_HAND, OFF_HAND, HELMET, CHESTPLATE, LEGGINGS, BOOTS
    }

    private final EquipmentSlot slot;
    private final ItemData itemData;

    public EquipmentAction(EquipmentSlot slot, ItemData itemData) {
        super();
        this.slot = slot;
        this.itemData = itemData;
    }

    public EquipmentSlot getSlot() { return slot; }
    public ItemData getItemData() { return itemData; }

    // Gelişmiş ItemData sınıfı
    public static class ItemData implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String material;
        private final int amount;
        private final short durability;
        private final String displayName;
        private final List<String> lore;
        private final Map<String, Integer> enchantments;
        private final byte[] itemStackData; // Bukkit ItemStack serialized data
        private final int customModelData;
        private final boolean unbreakable;

        // Basit constructor (geriye uyumluluk için)
        public ItemData(String material, int amount, short durability, String displayName) {
            this.material = material;
            this.amount = amount;
            this.durability = durability;
            this.displayName = displayName;
            this.lore = null;
            this.enchantments = new HashMap<>();
            this.itemStackData = null;
            this.customModelData = 0;
            this.unbreakable = false;
        }

        // Full constructor with all data
        public ItemData(String material, int amount, short durability, String displayName,
                        List<String> lore, Map<String, Integer> enchantments, byte[] itemStackData,
                        int customModelData, boolean unbreakable) {
            this.material = material;
            this.amount = amount;
            this.durability = durability;
            this.displayName = displayName;
            this.lore = lore;
            this.enchantments = enchantments != null ? enchantments : new HashMap<>();
            this.itemStackData = itemStackData;
            this.customModelData = customModelData;
            this.unbreakable = unbreakable;
        }

        // Getters
        public String getMaterial() { return material; }
        public int getAmount() { return amount; }
        public short getDurability() { return durability; }
        public String getDisplayName() { return displayName; }
        public List<String> getLore() { return lore; }
        public Map<String, Integer> getEnchantments() { return enchantments; }
        public byte[] getItemStackData() { return itemStackData; }
        public int getCustomModelData() { return customModelData; }
        public boolean isUnbreakable() { return unbreakable; }

        @Override
        public String toString() {
            return "ItemData{" +
                    "material='" + material + '\'' +
                    ", amount=" + amount +
                    ", enchants=" + enchantments.size() +
                    ", hasData=" + (itemStackData != null) +
                    '}';
        }
    }
}