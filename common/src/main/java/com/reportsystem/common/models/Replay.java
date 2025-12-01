package com.reportsystem.common.models;

import com.reportsystem.common.replay.actions.ReplayAction;
import java.util.List;

public class Replay {
    private int id;
    private int reportId;
    private String recordedPlayer;
    private String recordedPlayerUuid;
    private String worldName;
    private long startTime;
    private long endTime;
    private long duration;
    private long createdAt;
    private int actionCount;
    private int viewCount;
    private byte[] data;
    private boolean compressed;
    private transient List<ReplayAction> actions;

    // Constructor 1: Yeni MySQL replay (ID'siz) - 9 parametre
    public Replay(int reportId, String recordedPlayer, String recordedPlayerUuid,
                  long duration, long createdAt, int actionCount, int viewCount,
                  byte[] data, boolean compressed) {
        this.id = -1;
        this.reportId = reportId;
        this.recordedPlayer = recordedPlayer;
        this.recordedPlayerUuid = recordedPlayerUuid;
        this.worldName = "world";
        this.duration = duration;
        this.createdAt = createdAt;
        this.startTime = createdAt;
        this.endTime = createdAt + (duration * 1000);
        this.actionCount = actionCount;
        this.viewCount = viewCount;
        this.data = data;
        this.compressed = compressed;
    }

    // Constructor 2: MySQL'den yükleme (ID'li) - 10 parametre
    public Replay(int id, int reportId, String recordedPlayer, String recordedPlayerUuid,
                  long duration, long createdAt, int actionCount, int viewCount,
                  byte[] data, boolean compressed) {
        this.id = id;
        this.reportId = reportId;
        this.recordedPlayer = recordedPlayer;
        this.recordedPlayerUuid = recordedPlayerUuid;
        this.worldName = "world";
        this.duration = duration;
        this.createdAt = createdAt;
        this.startTime = createdAt;
        this.endTime = createdAt + (duration * 1000);
        this.actionCount = actionCount;
        this.viewCount = viewCount;
        this.data = data;
        this.compressed = compressed;
    }

    // Constructor 3: SQLite için (eski format) - 8 parametre
    public Replay(int reportId, String recordedPlayer, String worldName, long startTime,
                  long endTime, int actionCount, byte[] data, boolean compressed) {
        this.id = -1;
        this.reportId = reportId;
        this.recordedPlayer = recordedPlayer;
        this.recordedPlayerUuid = "";
        this.worldName = worldName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = (endTime - startTime) / 1000;
        this.createdAt = startTime;
        this.actionCount = actionCount;
        this.viewCount = 0;
        this.data = data;
        this.compressed = compressed;
    }

    // Constructor 4: RecordingManager için özel - 11 parametre
    public Replay(int id, int reportId, String recordedPlayer, String recordedPlayerUuid,
                  String worldName, long startTime, long endTime, int duration,
                  int actionCount, byte[] data, boolean compressed) {
        this.id = id;
        this.reportId = reportId;
        this.recordedPlayer = recordedPlayer;
        this.recordedPlayerUuid = recordedPlayerUuid;
        this.worldName = worldName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.createdAt = startTime;
        this.actionCount = actionCount;
        this.viewCount = 0;
        this.data = data;
        this.compressed = compressed;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
    }

    public String getRecordedPlayer() {
        return recordedPlayer;
    }

    public void setRecordedPlayer(String recordedPlayer) {
        this.recordedPlayer = recordedPlayer;
    }

    public String getRecordedPlayerUuid() {
        return recordedPlayerUuid;
    }

    public void setRecordedPlayerUuid(String recordedPlayerUuid) {
        this.recordedPlayerUuid = recordedPlayerUuid;
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

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getActionCount() {
        return actionCount;
    }

    public void setActionCount(int actionCount) {
        this.actionCount = actionCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public List<ReplayAction> getActions() {
        return actions;
    }

    public void setActions(List<ReplayAction> actions) {
        this.actions = actions;
    }
}