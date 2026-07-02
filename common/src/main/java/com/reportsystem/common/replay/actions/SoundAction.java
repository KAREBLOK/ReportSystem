package com.reportsystem.common.replay.actions;

public class SoundAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    private final String soundName;
    private final double x, y, z;
    private final float volume;
    private final float pitch;

    public SoundAction(String soundName, double x, double y, double z,
                       float volume, float pitch) {
        super();
        this.soundName = soundName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = volume;
        this.pitch = pitch;
    }

    public String getSoundName() {
        return soundName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }
}