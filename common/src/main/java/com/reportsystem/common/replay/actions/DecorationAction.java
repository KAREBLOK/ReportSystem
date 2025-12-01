package com.reportsystem.common.replay.actions;

/**
 * Dekoratif blok etkileşimleri (jukebox, flower pot, lectern, armor stand)
 */
public class DecorationAction extends ReplayAction {

    public enum DecorationType {
        JUKEBOX_INSERT,     // Jukebox'a müzik diski koyma
        JUKEBOX_EJECT,      // Jukebox'tan disk çıkarma
        FLOWER_POT_PLANT,   // Flower pot'a çiçek koyma
        FLOWER_POT_REMOVE,  // Flower pot'tan çiçek alma
        LECTERN_TAKE_BOOK,  // Lectern'den kitap alma
        LECTERN_PLACE_BOOK, // Lectern'e kitap koyma
        ARMOR_STAND_EQUIP,  // Armor stand'e eşya koyma
        ARMOR_STAND_TAKE    // Armor stand'den eşya alma
    }

    private final DecorationType decorationType;
    private final int x, y, z;
    private final String itemData; // Serialized ItemStack (disc, flower, book, armor)

    public DecorationAction(DecorationType decorationType, int x, int y, int z, String itemData) {
        super();
        this.decorationType = decorationType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemData = itemData;
    }

    public DecorationType getDecorationType() { return decorationType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getItemData() { return itemData; }
}
