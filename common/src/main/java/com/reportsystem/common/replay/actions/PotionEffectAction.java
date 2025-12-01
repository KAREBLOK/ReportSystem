package com.reportsystem.common.replay.actions;

public class PotionEffectAction extends ReplayAction {
    public enum ActionType {
        ADD,
        REMOVE,
        CLEAR_ALL
    }

    private final ActionType actionType;
    private final String effectType;
    private final int amplifier;
    private final int duration;
    private final boolean ambient;
    private final boolean particles;

    public PotionEffectAction(ActionType actionType, String effectType,
                              int amplifier, int duration, boolean ambient, boolean particles) {
        super();
        this.actionType = actionType;
        this.effectType = effectType;
        this.amplifier = amplifier;
        this.duration = duration;
        this.ambient = ambient;
        this.particles = particles;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public String getEffectType() {
        return effectType;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isAmbient() {
        return ambient;
    }

    public boolean hasParticles() {
        return particles;
    }
}