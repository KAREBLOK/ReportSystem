package com.reportsystem.common.replay.actions;

/**
 * Oyuncu durum değişiklikleri (sneak, sprint, glide)
 */
public class PlayerStateAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum StateType {
        SNEAK,      // Çömelme
        SPRINT,     // Koşma
        GLIDE       // Elytra uçuşu
    }

    private final StateType stateType;
    private final boolean enabled; // true = başladı, false = durdu

    public PlayerStateAction(StateType stateType, boolean enabled) {
        super();
        this.stateType = stateType;
        this.enabled = enabled;
    }

    public StateType getStateType() { return stateType; }
    public boolean isEnabled() { return enabled; }
}
