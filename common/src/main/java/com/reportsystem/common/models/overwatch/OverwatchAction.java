package com.reportsystem.common.models.overwatch;

public class OverwatchAction {
    private final int reportId;
    private final String actionType; // AUTO_BAN, AUTO_MUTE, STAFF_REVIEW, INSUFFICIENT_EVIDENCE
    private final String reason;
    private final double consensusPercentage;
    private final int totalReviewers;
    private final int guiltyCount;
    private final long executedAt;
    private final String executedBy;

    public OverwatchAction(int reportId, String actionType, String reason,
                          double consensusPercentage, int totalReviewers, int guiltyCount,
                          long executedAt, String executedBy) {
        this.reportId = reportId;
        this.actionType = actionType;
        this.reason = reason;
        this.consensusPercentage = consensusPercentage;
        this.totalReviewers = totalReviewers;
        this.guiltyCount = guiltyCount;
        this.executedAt = executedAt;
        this.executedBy = executedBy;
    }

    public int getReportId() { return reportId; }
    public String getActionType() { return actionType; }
    public String getReason() { return reason; }
    public double getConsensusPercentage() { return consensusPercentage; }
    public int getTotalReviewers() { return totalReviewers; }
    public int getGuiltyCount() { return guiltyCount; }
    public long getExecutedAt() { return executedAt; }
    public String getExecutedBy() { return executedBy; }
}
