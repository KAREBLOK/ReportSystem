package com.reportsystem.common.replay.actions;

/**
 * Tarım mekaniği (bone meal, hoe, harvest, farmland trample)
 */
public class FarmingAction extends ReplayAction {

    public enum FarmingType {
        BONE_MEAL,      // Kemik tozu kullanma
        HOE_TILL,       // Çapa ile toprak çevirme
        CROP_HARVEST,   // Ekin hasadı
        FARMLAND_TRAMPLE // Tarlanın üzerine atlama
    }

    private final FarmingType farmingType;
    private final int x, y, z;
    private final String blockType; // Etkilenen blok türü

    public FarmingAction(FarmingType farmingType, int x, int y, int z, String blockType) {
        super();
        this.farmingType = farmingType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
    }

    public FarmingType getFarmingType() { return farmingType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getBlockType() { return blockType; }
}
