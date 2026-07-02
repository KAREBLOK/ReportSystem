package com.reportsystem.common.replay.actions;

/**
 * Ateş yakma ve yanma (ignite, burn)
 */
public class BlockIgniteAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum IgniteType {
        FLINT_AND_STEEL,    // Çakmak
        LAVA,               // Lav
        FIRE_CHARGE,        // Fire charge
        LIGHTNING,          // Yıldırım
        SPREAD              // Ateş yayılması
    }

    private final IgniteType igniteType;
    private final int x, y, z;

    public BlockIgniteAction(IgniteType igniteType, int x, int y, int z) {
        super();
        this.igniteType = igniteType;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public IgniteType getIgniteType() { return igniteType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}
