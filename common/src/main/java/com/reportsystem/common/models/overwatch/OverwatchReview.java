package com.reportsystem.common.models.overwatch;

public class OverwatchReview {
    private final int id;
    private final int reportId;
    private final String reviewerUUID;
    private final String reviewerName;
    private final String verdict; // GUILTY, INNOCENT, SKIP
    private final String category; // Combat, Movement, Visual, Other
    private final String subcategory; // KillAura, Fly, XRay, etc.
    private final int confidence; // 0-100
    private final String notes;
    private final long reviewedAt;
    private final int reviewDuration; // seconds
    private final String serverName;

    public OverwatchReview(int id, int reportId, String reviewerUUID, String reviewerName,
                          String verdict, String category, String subcategory, int confidence,
                          String notes, long reviewedAt, int reviewDuration, String serverName) {
        this.id = id;
        this.reportId = reportId;
        this.reviewerUUID = reviewerUUID;
        this.reviewerName = reviewerName;
        this.verdict = verdict;
        this.category = category;
        this.subcategory = subcategory;
        this.confidence = confidence;
        this.notes = notes;
        this.reviewedAt = reviewedAt;
        this.reviewDuration = reviewDuration;
        this.serverName = serverName;
    }

    public int getId() { return id; }
    public int getReportId() { return reportId; }
    public String getReviewerUUID() { return reviewerUUID; }
    public String getReviewerName() { return reviewerName; }
    public String getVerdict() { return verdict; }
    public String getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public int getConfidence() { return confidence; }
    public String getNotes() { return notes; }
    public long getReviewedAt() { return reviewedAt; }
    public int getReviewDuration() { return reviewDuration; }
    public String getServerName() { return serverName; }
}
