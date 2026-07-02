package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.VehicleAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class VehicleListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public VehicleListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!event.isCancelled() && event.getEntered() instanceof Player) {
            Player player = (Player) event.getEntered();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Vehicle vehicle = event.getVehicle();
                VehicleAction.VehicleType vehicleType = getVehicleType(vehicle);

                if (vehicleType != null) {
                    Location vehicleLoc = vehicle.getLocation();
                    VehicleAction action = new VehicleAction(
                            VehicleAction.ActionType.MOUNT,
                            vehicleType,
                            vehicleLoc.getX(),
                            vehicleLoc.getY(),
                            vehicleLoc.getZ()
                    );
                    session.addAction(action);

                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Player mounted " + vehicleType);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!event.isCancelled() && event.getExited() instanceof Player) {
            Player player = (Player) event.getExited();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Vehicle vehicle = event.getVehicle();
                VehicleAction.VehicleType vehicleType = getVehicleType(vehicle);

                if (vehicleType != null) {
                    Location vehicleLoc = vehicle.getLocation();
                    VehicleAction action = new VehicleAction(
                            VehicleAction.ActionType.DISMOUNT,
                            vehicleType,
                            vehicleLoc.getX(),
                            vehicleLoc.getY(),
                            vehicleLoc.getZ()
                    );
                    session.addAction(action);

                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Player dismounted " + vehicleType);
                }
            }
        }
    }

    // EntityMountEvent - At, domuz, llama gibi entity'lere binme için
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityMount(EntityMountEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Entity mount = event.getMount();
            VehicleAction.VehicleType vehicleType = getEntityVehicleType(mount);

            if (vehicleType != null) {
                Location mountLoc = mount.getLocation();
                VehicleAction action = new VehicleAction(
                        VehicleAction.ActionType.MOUNT,
                        vehicleType,
                        mountLoc.getX(),
                        mountLoc.getY(),
                        mountLoc.getZ()
                );
                session.addAction(action);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Player mounted " + vehicleType +
                        " (Entity) at " + mountLoc);
            }
        }
    }

    // EntityDismountEvent - At, domuz, llama gibi entity'lerden inme için
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Entity mount = event.getDismounted();
            VehicleAction.VehicleType vehicleType = getEntityVehicleType(mount);

            if (vehicleType != null) {
                Location mountLoc = mount.getLocation();
                VehicleAction action = new VehicleAction(
                        VehicleAction.ActionType.DISMOUNT,
                        vehicleType,
                        mountLoc.getX(),
                        mountLoc.getY(),
                        mountLoc.getZ()
                );
                session.addAction(action);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Player dismounted " + vehicleType +
                        " (Entity) at " + mountLoc);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleSpawn(EntitySpawnEvent event) {
        if (event.isCancelled()) return;

        Entity entity = event.getEntity();

        // Sadece vehicleları kaydet
        if (!(entity instanceof Vehicle)) return;

        Vehicle vehicle = (Vehicle) entity;
        VehicleAction.VehicleType vehicleType = getVehicleType(vehicle);

        if (vehicleType != null) {
            // Yakındaki kaydedilen oyuncuları bul (10 blok)
            for (Player player : entity.getLocation().getWorld().getPlayers()) {
                if (player.getLocation().distance(entity.getLocation()) <= 10) {
                    RecordingSession session = recordingManager.getSession(player.getUniqueId());

                    if (session != null) {
                        Location loc = vehicle.getLocation();
                        VehicleAction action = new VehicleAction(
                                VehicleAction.ActionType.PLACE,
                                vehicleType,
                                loc.getX(),
                                loc.getY(),
                                loc.getZ()
                        );
                        session.addAction(action);

                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Vehicle placed: " + vehicleType +
                                " at " + loc + " by " + player.getName());
                        break; // Sadece en yakın oyuncu kaydeder
                    }
                }
            }
        }
    }

    private VehicleAction.VehicleType getVehicleType(Vehicle vehicle) {
        // Sadece gerçek Vehicle'lar için (Minecart, Boat)
        if (vehicle instanceof Minecart) return VehicleAction.VehicleType.MINECART;
        if (vehicle instanceof Boat) return VehicleAction.VehicleType.BOAT;
        return null;
    }

    /**
     * Entity'leri VehicleType'a dönüştürür (Horse, Pig, Llama, Camel, Strider için)
     */
    private VehicleAction.VehicleType getEntityVehicleType(Entity entity) {
        if (entity instanceof Horse) return VehicleAction.VehicleType.HORSE;
        if (entity instanceof Pig) return VehicleAction.VehicleType.PIG;
        if (entity instanceof Strider) return VehicleAction.VehicleType.STRIDER;
        if (entity instanceof Llama) return VehicleAction.VehicleType.LLAMA;
        if (entity instanceof Camel) return VehicleAction.VehicleType.CAMEL;
        // Minecart ve Boat da Entity olduğu için onları da kontrol edelim
        if (entity instanceof Minecart) return VehicleAction.VehicleType.MINECART;
        if (entity instanceof Boat) return VehicleAction.VehicleType.BOAT;
        return null;
    }
}