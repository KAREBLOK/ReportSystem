package com.reportsystem.spigot.recording;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.reportsystem.common.replay.actions.ReplayAction;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.*;

public class RecordingSession {
    private final String recordedPlayerName;
    private final List<ReplayAction> actions = Collections.synchronizedList(new ArrayList<>());
    private PacketListenerAbstract packetListener;
    private String worldName;
    private long startTime;
    private long endTime;

    // Spawned entity tracking için
    private final Map<UUID, Entity> spawnedEntities = new HashMap<>();
    private final Map<UUID, Location> lastEntityLocations = new HashMap<>();

    public RecordingSession(String recordedPlayerName) {
        this.recordedPlayerName = recordedPlayerName;
    }

    public void addAction(ReplayAction action) {
        this.actions.add(action);
    }

    public List<ReplayAction> getActions() {
        return actions;
    }

    public String getRecordedPlayerName() {
        return recordedPlayerName;
    }

    public PacketListenerAbstract getPacketListener() {
        return packetListener;
    }

    public void setPacketListener(PacketListenerAbstract packetListener) {
        this.packetListener = packetListener;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    // Spawned entity tracking metodları
    public void trackSpawnedEntity(Entity entity) {
        spawnedEntities.put(entity.getUniqueId(), entity);
        lastEntityLocations.put(entity.getUniqueId(), entity.getLocation().clone());
    }

    public Map<UUID, Entity> getSpawnedEntities() {
        return spawnedEntities;
    }

    public Map<UUID, Location> getLastEntityLocations() {
        return lastEntityLocations;
    }

    public void updateEntityLocation(UUID uuid, Location location) {
        lastEntityLocations.put(uuid, location.clone());
    }

    public void removeSpawnedEntity(UUID uuid) {
        spawnedEntities.remove(uuid);
        lastEntityLocations.remove(uuid);
    }
}