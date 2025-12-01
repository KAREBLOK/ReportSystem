package com.reportsystem.common.replay.actions;

public class BedAction extends ReplayAction {

    public enum ActionType {
        ENTER_BED,
        LEAVE_BED
    }

    private final ActionType actionType;
    private final int bedX;
    private final int bedY;
    private final int bedZ;

    public BedAction(ActionType actionType, int x, int y, int z) {
        super();
        this.actionType = actionType;
        this.bedX = x;
        this.bedY = y;
        this.bedZ = z;
    }

    public ActionType getActionType() { return actionType; }
    public int getBedX() { return bedX; }
    public int getBedY() { return bedY; }
    public int getBedZ() { return bedZ; }
}