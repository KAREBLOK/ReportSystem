package com.reportsystem.common.replay.actions;

public class HealthAction extends ReplayAction {
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;

    public HealthAction(double health, double maxHealth, int foodLevel, float saturation) {
        super();
        this.health = health;
        this.maxHealth = maxHealth;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
    }

    public double getHealth() {
        return health;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public float getSaturation() {
        return saturation;
    }
}