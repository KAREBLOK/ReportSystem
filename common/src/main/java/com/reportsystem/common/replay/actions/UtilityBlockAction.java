package com.reportsystem.common.replay.actions;

/**
 * Utility block kullanımı (composter, grindstone, smithing, stonecutter, loom, cartography)
 */
public class UtilityBlockAction extends ReplayAction {

    public enum UtilityType {
        COMPOSTER,      // Kompost yapma
        GRINDSTONE,     // Büyü kaldırma, tamir
        SMITHING,       // Netherite upgrade
        STONECUTTER,    // Taş kesme
        LOOM,           // Banner deseni
        CARTOGRAPHY     // Harita klonlama/büyütme
    }

    private final UtilityType utilityType;
    private final int x, y, z;
    private final String itemBefore;  // Serialized ItemStack before
    private final String itemAfter;   // Serialized ItemStack after

    public UtilityBlockAction(UtilityType utilityType, int x, int y, int z,
                             String itemBefore, String itemAfter) {
        super();
        this.utilityType = utilityType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemBefore = itemBefore;
        this.itemAfter = itemAfter;
    }

    public UtilityType getUtilityType() { return utilityType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getItemBefore() { return itemBefore; }
    public String getItemAfter() { return itemAfter; }
}
