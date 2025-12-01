package com.reportsystem.common.models.overwatch;

public class OverwatchQueueItem {
    private final int reportId;
    private final int priority;
    private final long addedAt;
    private final String assignedTo;
    private final long assignedAt;
    private final String assignedServer;
    private final String status;

    public OverwatchQueueItem(int reportId, int priority, long addedAt, String assignedTo,
                              long assignedAt, String assignedServer, String status) {
        this.reportId = reportId;
        this.priority = priority;
        this.addedAt = addedAt;
        this.assignedTo = assignedTo;
        this.assignedAt = assignedAt;
        this.assignedServer = assignedServer;
        this.status = status;
    }

    public int getReportId() { return reportId; }
    public int getPriority() { return priority; }
    public long getAddedAt() { return addedAt; }
    public String getAssignedTo() { return assignedTo; }
    public long getAssignedAt() { return assignedAt; }
    public String getAssignedServer() { return assignedServer; }
    public String getStatus() { return status; }
}
