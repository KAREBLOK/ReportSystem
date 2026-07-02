package com.reportsystem.common.replay.actions;

public class HealthAction extends ReplayAction {
    private static final long serialVersionUID = 5660854273163407253L;

    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;
    private double absorption; // Eski replay verileriyle uyumlu - varsayılan 0.0

    public HealthAction(double health, double maxHealth, int foodLevel, float saturation) {
        super();
        this.health = health;
        this.maxHealth = maxHealth;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.absorption = 0.0;
    }

    public HealthAction(double health, double maxHealth, int foodLevel, float saturation, double absorption) {
        super();
        this.health = health;
        this.maxHealth = maxHealth;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.absorption = absorption;
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

    public double getAbsorption() {
        return absorption;
    }
}