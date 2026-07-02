package com.reportsystem.common.replay.actions;

/**
 * Konteyner açma (ender chest, shulker box, barrel, hopper, chest)
 */
public class ContainerAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum ContainerType {
        CHEST,
        ENDER_CHEST,
        SHULKER_BOX,
        BARREL,
        HOPPER,
        FURNACE,
        DISPENSER,
        DROPPER
    }

    private final ContainerType containerType;
    private final int x, y, z;
    private final boolean opened; // true = açıldı, false = kapandı

    public ContainerAction(ContainerType containerType, int x, int y, int z, boolean opened) {
        super();
        this.containerType = containerType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.opened = opened;
    }

    public ContainerType getContainerType() { return containerType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public boolean isOpened() { return opened; }
}
