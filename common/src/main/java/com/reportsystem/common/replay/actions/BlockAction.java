package com.reportsystem.common.replay.actions;

public class BlockAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum BlockActionType {
        START_BREAKING,    // Blok kırmaya başlama
        STOP_BREAKING,     // Blok kırmayı durdurma
        BREAK_PROGRESS,    // Kırma ilerlemesi (0-9)
        PLACE_BLOCK,       // Blok yerleştirme
        INTERACT_BLOCK     // Blokla etkileşim (kapı açma vb)
    }

    private final BlockActionType actionType;
    private final int x, y, z;
    private final int stage; // Kırma aşaması (0-9)
    private final String blockType; // Yerleştirilen blok tipi

    // Blok kırma için
    public BlockAction(BlockActionType actionType, int x, int y, int z, int stage) {
        super();
        this.actionType = actionType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.stage = stage;
        this.blockType = null;
    }

    // Blok yerleştirme için
    public BlockAction(BlockActionType actionType, int x, int y, int z, String blockType) {
        super();
        this.actionType = actionType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.stage = 0;
        this.blockType = blockType;
    }

    // Blok kırma + blok tipi için (START_BREAKING - kırılan bloğun tipini kaydetmek için)
    public BlockAction(BlockActionType actionType, int x, int y, int z, int stage, String blockType) {
        super();
        this.actionType = actionType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.stage = stage;
        this.blockType = blockType;
    }

    // Getter'lar
    public BlockActionType getActionType() { return actionType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getStage() { return stage; }
    public String getBlockType() { return blockType; }
}