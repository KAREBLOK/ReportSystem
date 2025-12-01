package com.reportsystem.common.replay.actions;

public class AnimationAction extends ReplayAction {
    public enum AnimationType {
        SWING_MAIN_HAND,
        SWING_OFF_HAND,
        TAKE_DAMAGE
        // ... diğer animasyonlar eklenebilir
    }

    private final AnimationType animationType;

    public AnimationAction(AnimationType animationType) {
        super();
        this.animationType = animationType;
    }

    public AnimationType getAnimationType() {
        return animationType;
    }
}