package com.reportsystem.common.replay.actions;

public class VehicleAction extends ReplayAction {

    public enum VehicleType {
        MINECART,
        BOAT,
        HORSE,
        PIG,
        STRIDER,
        LLAMA,
        CAMEL
    }

    public enum ActionType {
        MOUNT,    // Araca bin
        DISMOUNT, // Araçtan in
        PLACE     // Aracı yerleştir (minecart, boat)
    }

    private final ActionType actionType;
    private final VehicleType vehicleType;
    private final double vehicleX;
    private final double vehicleY;
    private final double vehicleZ;

    public VehicleAction(ActionType actionType, VehicleType vehicleType,
                         double x, double y, double z) {
        super();
        this.actionType = actionType;
        this.vehicleType = vehicleType;
        this.vehicleX = x;
        this.vehicleY = y;
        this.vehicleZ = z;
    }

    public ActionType getActionType() { return actionType; }
    public VehicleType getVehicleType() { return vehicleType; }
    public double getVehicleX() { return vehicleX; }
    public double getVehicleY() { return vehicleY; }
    public double getVehicleZ() { return vehicleZ; }
}