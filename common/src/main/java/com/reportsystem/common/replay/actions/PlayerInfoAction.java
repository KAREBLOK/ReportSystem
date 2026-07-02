package com.reportsystem.common.replay.actions;

public class PlayerInfoAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    private final String skinTexture;
    private final String skinSignature;
    private final String displayName;

    public PlayerInfoAction(String skinTexture, String skinSignature, String displayName) {
        super();
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
        this.displayName = displayName;
    }

    public String getSkinTexture() { return skinTexture; }
    public String getSkinSignature() { return skinSignature; }
    public String getDisplayName() { return displayName; }
}