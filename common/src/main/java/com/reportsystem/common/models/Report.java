package com.reportsystem.common.models;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Report {
    private int id;
    private String reporter;
    private String reporterName;
    private String reporterUuid;
    private String reportedPlayer;
    private String reportedPlayerName;
    private String reason;
    private String serverName;
    private String status;
    private String assignedTo;
    private String response;
    private long createdAt;
    private long updatedAt;
    private long timestamp;
    private boolean punished;
    private String punishmentType;
    private String resolvedBy; // EKLENDİ
    private long resolvedAt;   // EKLENDİ

    // Status enum (İÇİNDEKİLER GÜNCELLENDİ)
    public enum Status {
        PENDING("PENDING"),
        ACCEPTED("ACCEPTED"),
        REJECTED("REJECTED"),
        IN_PROGRESS("IN_PROGRESS"),
        CLOSED("CLOSED");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Status fromString(String value) {
            for (Status s : Status.values()) {
                if (s.value.equalsIgnoreCase(value)) {
                    return s;
                }
            }
            return PENDING;
        }
    }

    // EKLENDİ - GUIListener'ın ihtiyaç duyduğu enum
    public enum ReportStatus {
        PENDING, ACCEPTED, REJECTED, IN_PROGRESS, CLOSED
    }


    // Constructors
    public Report() {
        this.status = Status.PENDING.getValue();
        this.timestamp = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.punished = false;
    }

    public Report(String reporter, String reportedPlayer, String reason, String serverName) {
        this();
        this.reporter = reporter;
        this.reporterName = reporter;
        this.reportedPlayer = reportedPlayer;
        this.reportedPlayerName = reportedPlayer;
        this.reason = reason;
        this.serverName = serverName;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getReporterUuid() {
        return reporterUuid;
    }

    public void setReporterUuid(String reporterUuid) {
        this.reporterUuid = reporterUuid;
    }

    public String getReportedPlayer() {
        return reportedPlayer;
    }

    public void setReportedPlayer(String reportedPlayer) {
        this.reportedPlayer = reportedPlayer;
    }

    public String getReportedPlayerName() {
        return reportedPlayerName;
    }

    public void setReportedPlayerName(String reportedPlayerName) {
        this.reportedPlayerName = reportedPlayerName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStatus(Status status) {
        this.status = status.getValue();
    }

    // EKLENDİ
    public void setStatus(ReportStatus status) {
        this.status = status.name();
    }

    public Status getStatusEnum() {
        return Status.fromString(status);
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isPunished() {
        return punished;
    }

    public void setPunished(boolean punished) {
        this.punished = punished;
    }

    public String getPunishmentType() {
        return punishmentType;
    }

    public void setPunishmentType(String punishmentType) {
        this.punishmentType = punishmentType;
    }

    // EKLENDİ
    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolvedBy() {
        return this.resolvedBy;
    }

    // EKLENDİ
    public void setResolvedAt(long resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public long getResolvedAt() {
        return this.resolvedAt;
    }


    // Utility methods
    public boolean isPending() {
        return Status.PENDING.getValue().equals(status);
    }

    public boolean isAccepted() {
        return Status.ACCEPTED.getValue().equals(status);
    }

    public boolean isRejected() {
        return Status.REJECTED.getValue().equals(status);
    }

    public boolean isClosed() {
        return Status.CLOSED.getValue().equals(status);
    }

    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    @Override
    public String toString() {
        return "Report{" +
                "id=" + id +
                ", reporter='" + reporter + '\'' +
                ", reportedPlayer='" + reportedPlayer + '\'' +
                ", reason='" + reason + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}