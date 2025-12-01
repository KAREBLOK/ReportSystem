package com.reportsystem.common.replay.actions;

public class GameModeAction extends ReplayAction {
    public enum GameMode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR
    }

    private final GameMode gameMode;

    public GameModeAction(GameMode gameMode) {
        super();
        this.gameMode = gameMode;
    }

    public GameMode getGameMode() {
        return gameMode;
    }
}