package com.reportsystem.common.replay.actions;

public class WeatherAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum WeatherType {
        CLEAR,
        RAIN,
        THUNDER
    }

    private final WeatherType weatherType;
    private final boolean isPlayerSpecific; // Sadece bu oyuncu mu görüyor

    public WeatherAction(WeatherType weatherType, boolean isPlayerSpecific) {
        super();
        this.weatherType = weatherType;
        this.isPlayerSpecific = isPlayerSpecific;
    }

    public WeatherType getWeatherType() {
        return weatherType;
    }

    public boolean isPlayerSpecific() {
        return isPlayerSpecific;
    }
}