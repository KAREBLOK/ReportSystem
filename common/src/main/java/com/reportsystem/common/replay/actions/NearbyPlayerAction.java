package com.reportsystem.common.replay.actions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NearbyPlayerAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum ActionType {
        PLAYER_APPEAR,    // Oyuncu görünür oldu
        PLAYER_DISAPPEAR, // Oyuncu görünmez oldu
        PLAYER_MOVE       // Oyuncu hareket etti
    }

    private final ActionType actionType;
    private final UUID playerUuid;
    private final String playerName;
    private final double x, y, z;
    private final float yaw, pitch;
    private final String skinTexture;
    private final String skinSignature;
    private final Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipment;
    private final Map<Integer, EquipmentAction.ItemData> inventory; // Full inventory (slot -> item)

    // Eski constructor (geriye uyumluluk için)
    public NearbyPlayerAction(ActionType actionType, UUID playerUuid, String playerName,
                              double x, double y, double z, float yaw, float pitch,
                              String skinTexture, String skinSignature) {
        this(actionType, playerUuid, playerName, x, y, z, yaw, pitch, skinTexture, skinSignature, null, null);
    }

    // Constructor - equipment ile (geriye uyumluluk)
    public NearbyPlayerAction(ActionType actionType, UUID playerUuid, String playerName,
                              double x, double y, double z, float yaw, float pitch,
                              String skinTexture, String skinSignature,
                              Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipment) {
        this(actionType, playerUuid, playerName, x, y, z, yaw, pitch, skinTexture, skinSignature, equipment, null);
    }

    // Tam constructor - equipment ve inventory ile
    public NearbyPlayerAction(ActionType actionType, UUID playerUuid, String playerName,
                              double x, double y, double z, float yaw, float pitch,
                              String skinTexture, String skinSignature,
                              Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipment,
                              Map<Integer, EquipmentAction.ItemData> inventory) {
        super();
        this.actionType = actionType;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
        this.equipment = equipment != null ? equipment : new HashMap<>();
        this.inventory = inventory != null ? inventory : new HashMap<>();
    }

    // Getter'lar
    public ActionType getActionType() { return actionType; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getSkinTexture() { return skinTexture; }
    public String getSkinSignature() { return skinSignature; }
    public Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> getEquipment() { return equipment; }
    public Map<Integer, EquipmentAction.ItemData> getInventory() { return inventory; }
}