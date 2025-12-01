package com.reportsystem.common.replay.actions;

/**
 * Kova kullanımı (su, lav, süt, balık)
 */
public class BucketAction extends ReplayAction {

    public enum BucketType {
        WATER,
        LAVA,
        MILK,
        POWDER_SNOW,
        FISH // Balık kovası
    }

    public enum ActionType {
        FILL,  // Kova doldurma
        EMPTY  // Kova boşaltma
    }

    private final ActionType actionType;
    private final BucketType bucketType;
    private final int blockX, blockY, blockZ;

    public BucketAction(ActionType actionType, BucketType bucketType,
                       int blockX, int blockY, int blockZ) {
        super();
        this.actionType = actionType;
        this.bucketType = bucketType;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    public ActionType getActionType() { return actionType; }
    public BucketType getBucketType() { return bucketType; }
    public int getBlockX() { return blockX; }
    public int getBlockY() { return blockY; }
    public int getBlockZ() { return blockZ; }
}
