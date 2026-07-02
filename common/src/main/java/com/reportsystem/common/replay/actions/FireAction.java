package com.reportsystem.common.replay.actions;

public class FireAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    private final boolean onFire;
    private final int fireTicks;

    public FireAction(boolean onFire, int fireTicks) {
        super();
        this.onFire = onFire;
        this.fireTicks = fireTicks;
    }

    public boolean isOnFire() { return onFire; }
    public int getFireTicks() { return fireTicks; }
}