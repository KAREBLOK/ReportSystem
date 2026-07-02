package com.reportsystem.common.replay.actions;

public class UseItemAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum UseType {
        BOW_CHARGE,      // Yay çekme
        SHIELD_BLOCK,    // Kalkan kaldırma
        FOOD_EAT,        // Yemek yeme
        POTION_DRINK,    // İksir içme
        MILK_DRINK,      // Süt içme
        FISHING_ROD,     // Olta kullanma
        CROSSBOW_CHARGE, // Arbalet doldurma
        SPYGLASS,        // Dürbün kullanma
        GOAT_HORN,       // Keçi boynuzu
        TRIDENT_CHARGE   // Trident şarj etme (Riptide)
    }

    private final UseType useType;
    private final int duration; // Kullanım süresi (tick)
    private final boolean mainHand;
    private final boolean started; // true = başladı, false = bitti
    private final String itemData; // Serialized ItemStack for food/potion details

    public UseItemAction(UseType useType, int duration, boolean mainHand, boolean started) {
        this(useType, duration, mainHand, started, null);
    }

    public UseItemAction(UseType useType, int duration, boolean mainHand, boolean started, String itemData) {
        super();
        this.useType = useType;
        this.duration = duration;
        this.mainHand = mainHand;
        this.started = started;
        this.itemData = itemData;
    }

    public UseType getUseType() { return useType; }
    public int getDuration() { return duration; }
    public boolean isMainHand() { return mainHand; }
    public boolean isStarted() { return started; }
    public String getItemData() { return itemData; }
}