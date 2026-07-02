package com.reportsystem.common.replay.actions;

/**
 * Anvil kullanımı (item renaming/repairing)
 */
public class AnvilAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum AnvilActionType {
        RENAME,     // Item renaming
        REPAIR,     // Item repairing
        COMBINE     // Item combining
    }

    private final AnvilActionType actionType;
    private final String itemBefore; // Serialized ItemStack before
    private final String itemAfter;  // Serialized ItemStack after
    private final int expCost;       // Experience cost

    public AnvilAction(AnvilActionType actionType, String itemBefore, String itemAfter, int expCost) {
        super();
        this.actionType = actionType;
        this.itemBefore = itemBefore;
        this.itemAfter = itemAfter;
        this.expCost = expCost;
    }

    public AnvilActionType getActionType() { return actionType; }
    public String getItemBefore() { return itemBefore; }
    public String getItemAfter() { return itemAfter; }
    public int getExpCost() { return expCost; }
}
