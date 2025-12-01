package com.reportsystem.common.replay.actions;

public class DeathAction extends ReplayAction {

    private final String deathMessage;
    private final double deathX;
    private final double deathY;
    private final double deathZ;

    public DeathAction(String deathMessage, double x, double y, double z) {
        super();
        this.deathMessage = deathMessage;
        this.deathX = x;
        this.deathY = y;
        this.deathZ = z;
    }

    public String getDeathMessage() { return deathMessage; }
    public double getDeathX() { return deathX; }
    public double getDeathY() { return deathY; }
    public double getDeathZ() { return deathZ; }
}