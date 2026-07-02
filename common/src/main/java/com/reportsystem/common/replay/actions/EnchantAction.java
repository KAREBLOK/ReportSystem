package com.reportsystem.common.replay.actions;

import java.util.Map;

/**
 * Enchanting table kullanımı (item enchanting)
 */
public class EnchantAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    private final String itemBefore;  // Serialized ItemStack before enchanting
    private final String itemAfter;   // Serialized ItemStack after enchanting
    private final int expLevelCost;   // Experience level cost
    private final int enchantButton;  // Which button clicked (0-2 for top, middle, bottom)

    public EnchantAction(String itemBefore, String itemAfter, int expLevelCost, int enchantButton) {
        super();
        this.itemBefore = itemBefore;
        this.itemAfter = itemAfter;
        this.expLevelCost = expLevelCost;
        this.enchantButton = enchantButton;
    }

    public String getItemBefore() { return itemBefore; }
    public String getItemAfter() { return itemAfter; }
    public int getExpLevelCost() { return expLevelCost; }
    public int getEnchantButton() { return enchantButton; }
}
