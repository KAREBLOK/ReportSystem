package com.reportsystem.common.models.overwatch;

public class OverwatchStats {
    private final String reviewerUUID;
    private String reviewerName;
    private int totalReviews;
    private int guiltyVerdicts;
    private int innocentVerdicts;
    private int skippedVerdicts;
    private double accuracy;
    private int xp;
    private int level;
    private String rank; // BRONZE, SILVER, GOLD, DIAMOND
    private long firstReviewAt;
    private long lastReviewAt;
    private int totalReviewTime; // seconds

    public OverwatchStats(String reviewerUUID, String reviewerName, int totalReviews,
                         int guiltyVerdicts, int innocentVerdicts, int skippedVerdicts,
                         double accuracy, int xp, int level, String rank,
                         long firstReviewAt, long lastReviewAt, int totalReviewTime) {
        this.reviewerUUID = reviewerUUID;
        this.reviewerName = reviewerName;
        this.totalReviews = totalReviews;
        this.guiltyVerdicts = guiltyVerdicts;
        this.innocentVerdicts = innocentVerdicts;
        this.skippedVerdicts = skippedVerdicts;
        this.accuracy = accuracy;
        this.xp = xp;
        this.level = level;
        this.rank = rank;
        this.firstReviewAt = firstReviewAt;
        this.lastReviewAt = lastReviewAt;
        this.totalReviewTime = totalReviewTime;
    }

    // Create new stats for first-time reviewer
    public static OverwatchStats createNew(String reviewerUUID, String reviewerName) {
        return new OverwatchStats(
            reviewerUUID, reviewerName, 0, 0, 0, 0,
            100.0, 0, 1, "BRONZE",
            System.currentTimeMillis(), System.currentTimeMillis(), 0
        );
    }

    // Update stats after review
    public void addReview(String verdict, int reviewDuration) {
        this.totalReviews++;
        this.lastReviewAt = System.currentTimeMillis();
        this.totalReviewTime += reviewDuration;

        if (this.firstReviewAt == 0) {
            this.firstReviewAt = System.currentTimeMillis();
        }

        switch (verdict.toUpperCase()) {
            case "GUILTY":
                this.guiltyVerdicts++;
                break;
            case "INNOCENT":
                this.innocentVerdicts++;
                break;
            case "SKIP":
                this.skippedVerdicts++;
                break;
        }

        // Update XP (10 XP per review, +5 if not skipped)
        if (!"SKIP".equalsIgnoreCase(verdict)) {
            this.xp += 15;
        } else {
            this.xp += 5;
        }

        // Update level (every 100 XP = 1 level)
        this.level = 1 + (this.xp / 100);

        // Update rank based on XP
        if (this.xp >= 2500) {
            this.rank = "DIAMOND";
        } else if (this.xp >= 1000) {
            this.rank = "GOLD";
        } else if (this.xp >= 500) {
            this.rank = "SILVER";
        } else {
            this.rank = "BRONZE";
        }
    }

    // Getters and Setters
    public String getReviewerUUID() { return reviewerUUID; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public int getTotalReviews() { return totalReviews; }
    public int getGuiltyVerdicts() { return guiltyVerdicts; }
    public int getInnocentVerdicts() { return innocentVerdicts; }
    public int getSkippedVerdicts() { return skippedVerdicts; }
    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
    public int getXp() { return xp; }
    public int getLevel() { return level; }
    public String getRank() { return rank; }
    public long getFirstReviewAt() { return firstReviewAt; }
    public long getLastReviewAt() { return lastReviewAt; }
    public int getTotalReviewTime() { return totalReviewTime; }
}
