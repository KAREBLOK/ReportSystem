package com.reportsystem.spigot.replay;

/**
 * Oyuncunun replay tercihleri - replay'ler arasi kalici (sunucu restart'a kadar)
 */
public class ReplayPreferences {

    private boolean glowEnabled = false;
    private boolean nightVision = false;

    public boolean isGlowEnabled() {
        return glowEnabled;
    }

    public void setGlowEnabled(boolean glowEnabled) {
        this.glowEnabled = glowEnabled;
    }

    public boolean isNightVision() {
        return nightVision;
    }

    public void setNightVision(boolean nightVision) {
        this.nightVision = nightVision;
    }

}
