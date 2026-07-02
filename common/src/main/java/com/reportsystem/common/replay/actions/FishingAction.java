package com.reportsystem.common.replay.actions;

public class FishingAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum FishingState {
        CAST,       // Olta atma
        CAUGHT,     // Balık yakalama
        REEL_IN,    // Oltayı çekme
        FAILED      // Başarısız yakalama
    }

    private final FishingState state;
    private final double hookX, hookY, hookZ;
    private final String caughtItemData; // Serialized ItemStack of caught fish/treasure/junk

    public FishingAction(FishingState state, double x, double y, double z) {
        this(state, x, y, z, null);
    }

    public FishingAction(FishingState state, double x, double y, double z, String caughtItemData) {
        super();
        this.state = state;
        this.hookX = x;
        this.hookY = y;
        this.hookZ = z;
        this.caughtItemData = caughtItemData;
    }

    public FishingState getState() {
        return state;
    }

    public double getHookX() {
        return hookX;
    }

    public double getHookY() {
        return hookY;
    }

    public double getHookZ() {
        return hookZ;
    }

    public String getCaughtItemData() {
        return caughtItemData;
    }
}