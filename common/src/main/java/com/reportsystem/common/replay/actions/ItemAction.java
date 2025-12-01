package com.reportsystem.common.replay.actions;

public class ItemAction extends ReplayAction {

    public enum ItemActionType {
        DROP,       // Item atma
        PICKUP      // Item alma
    }

    private final ItemActionType actionType;
    private final String itemData; // Serialized ItemStack
    private final int amount;
    private final double x, y, z; // Item konumu

    public ItemAction(ItemActionType actionType, String itemData, int amount, double x, double y, double z) {
        super();
        this.actionType = actionType;
        this.itemData = itemData;
        this.amount = amount;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ItemActionType getActionType() { return actionType; }
    public String getItemData() { return itemData; }
    public int getAmount() { return amount; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}
