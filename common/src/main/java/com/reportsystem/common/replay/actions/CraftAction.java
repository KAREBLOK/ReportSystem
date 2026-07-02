package com.reportsystem.common.replay.actions;

/**
 * Crafting table kullanımı (item crafting)
 */
public class CraftAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    private final String craftedItem;  // Serialized ItemStack of crafted item
    private final int amount;          // Amount crafted
    private final boolean isShiftClick; // Shift-click for multiple crafts

    public CraftAction(String craftedItem, int amount, boolean isShiftClick) {
        super();
        this.craftedItem = craftedItem;
        this.amount = amount;
        this.isShiftClick = isShiftClick;
    }

    public String getCraftedItem() { return craftedItem; }
    public int getAmount() { return amount; }
    public boolean isShiftClick() { return isShiftClick; }
}
