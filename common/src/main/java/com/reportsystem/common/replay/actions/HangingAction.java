package com.reportsystem.common.replay.actions;

/**
 * Painting ve Item Frame yerleştirme/kırma
 */
public class HangingAction extends ReplayAction {

    public enum HangingType {
        PAINTING,
        ITEM_FRAME,
        GLOW_ITEM_FRAME
    }

    public enum ActionType {
        PLACE,
        BREAK
    }

    private final ActionType actionType;
    private final HangingType hangingType;
    private final double x, y, z;
    private final String facing; // NORTH, SOUTH, EAST, WEST
    private final String itemData; // Item Frame için item
    private final String paintingArt; // Painting için art tipi

    public HangingAction(ActionType actionType, HangingType hangingType,
                        double x, double y, double z, String facing, String itemData) {
        this(actionType, hangingType, x, y, z, facing, itemData, null);
    }

    public HangingAction(ActionType actionType, HangingType hangingType,
                        double x, double y, double z, String facing, String itemData, String paintingArt) {
        super();
        this.actionType = actionType;
        this.hangingType = hangingType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.itemData = itemData;
        this.paintingArt = paintingArt;
    }

    public ActionType getActionType() { return actionType; }
    public HangingType getHangingType() { return hangingType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getFacing() { return facing; }
    public String getItemData() { return itemData; }
    public String getPaintingArt() { return paintingArt; }
}
