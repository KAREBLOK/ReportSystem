package com.reportsystem.common.replay.actions;

/**
 * Redstone komponent ayarları (repeater, comparator, daylight sensor)
 */
public class RedstoneAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum RedstoneType {
        REPEATER_DELAY,     // Repeater gecikmesi ayarı
        COMPARATOR_MODE,    // Comparator modu (compare/subtract)
        DAYLIGHT_MODE       // Daylight sensor modu (day/night)
    }

    private final RedstoneType redstoneType;
    private final int x, y, z;
    private final int delay;        // Repeater için (1-4)
    private final String mode;      // Comparator/daylight için

    public RedstoneAction(RedstoneType redstoneType, int x, int y, int z, int delay, String mode) {
        super();
        this.redstoneType = redstoneType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.delay = delay;
        this.mode = mode;
    }

    public RedstoneType getRedstoneType() { return redstoneType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getDelay() { return delay; }
    public String getMode() { return mode; }
}
