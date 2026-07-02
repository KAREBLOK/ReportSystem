package com.reportsystem.common.replay.actions;

import java.util.UUID;

/**
 * Hayvan komutları ve özel etkileşimler (sit, shoulder parrot, horse armor, llama carpet)
 */
public class EntityCommandAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum CommandType {
        SIT,                // Kedi/kurt oturma
        STAND,              // Kedi/kurt kalkma
        PARROT_SHOULDER,    // Papağan omza çıkma
        PARROT_DISMOUNT,    // Papağan omuzdan inme
        HORSE_ARMOR,        // At zırhlama
        LLAMA_CARPET,       // Lama halı koyma
        HORSE_SADDLE,       // Eyer koyma
        PIG_SADDLE          // Domuz eyeri
    }

    private final CommandType commandType;
    private final UUID entityUuid;
    private final String entityType;
    private final double x, y, z;
    private final String itemData; // Armor/carpet için

    public EntityCommandAction(CommandType commandType, UUID entityUuid, String entityType,
                              double x, double y, double z, String itemData) {
        super();
        this.commandType = commandType;
        this.entityUuid = entityUuid;
        this.entityType = entityType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemData = itemData;
    }

    public CommandType getCommandType() { return commandType; }
    public UUID getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getItemData() { return itemData; }
}
