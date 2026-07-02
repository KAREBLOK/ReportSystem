package com.reportsystem.common.replay.actions;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action to record full inventory snapshot
 * Records all 41 inventory slots (36 inventory + 4 armor + 1 offhand)
 */
public class InventorySnapshotAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    private final Map<Integer, ItemData> inventoryContents; // Slot -> ItemData

    public InventorySnapshotAction(Map<Integer, ItemData> inventoryContents) {
        super();
        this.inventoryContents = inventoryContents;
    }

    public Map<Integer, ItemData> getInventoryContents() {
        return inventoryContents;
    }

    /**
     * ItemData class - same as EquipmentAction.ItemData but duplicated for compatibility
     */
    public static class ItemData implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String material;
        private final int amount;
        private final short durability;
        private final String displayName;
        private final List<String> lore;
        private final Map<String, Integer> enchantments;
        private final byte[] itemStackData;
        private final int customModelData;
        private final boolean unbreakable;

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
                    '}';
        }
    }
}
