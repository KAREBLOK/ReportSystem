package com.reportsystem.common.replay.actions;

/**
 * Sign yazma eylemi
 */
public class SignAction extends ReplayAction {

    private final int x, y, z;
    private final String[] lines; // 4 satır text
    private final String signType; // WALL_SIGN, STANDING_SIGN, etc.

    public SignAction(int x, int y, int z, String[] lines, String signType) {
        super();
        this.x = x;
        this.y = y;
        this.z = z;
        this.lines = lines != null ? lines : new String[4];
        this.signType = signType;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String[] getLines() { return lines; }
    public String getSignType() { return signType; }
}
