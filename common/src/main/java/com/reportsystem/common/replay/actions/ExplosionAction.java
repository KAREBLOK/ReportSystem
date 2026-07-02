package com.reportsystem.common.replay.actions;

public class ExplosionAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum ExplosionType {
        TNT,
        CREEPER,
        BED,
        END_CRYSTAL,
        FIREBALL,
        WITHER,
        ENDER_DRAGON,
        RESPAWN_ANCHOR,
        GENERIC
    }

    private final ExplosionType explosionType;
    private final double x, y, z;
    private final float power;
    private final boolean setFire; // Yangın çıkarır mı
    private final boolean breakBlocks; // Blokları kırar mı

    public ExplosionAction(ExplosionType explosionType, double x, double y, double z, float power, boolean setFire, boolean breakBlocks) {
        super();
        this.explosionType = explosionType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
        this.setFire = setFire;
        this.breakBlocks = breakBlocks;
    }

    public ExplosionType getExplosionType() { return explosionType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getPower() { return power; }
    public boolean isSetFire() { return setFire; }
    public boolean isBreakBlocks() { return breakBlocks; }
}
