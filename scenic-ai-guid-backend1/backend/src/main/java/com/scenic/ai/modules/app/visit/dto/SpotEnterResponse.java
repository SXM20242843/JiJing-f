package com.scenic.ai.modules.app.visit.dto;

public class SpotEnterResponse {

    public Long recordId;
    public Long visitId;
    public String scenicId;
    public Long spotId;
    public String spotName;
    public String enterTime;
    public CurrentSpot currentSpot;

    public SpotEnterResponse() {
    }

    public SpotEnterResponse(Long recordId, Long visitId, String scenicId, String enterTime) {
        this.recordId = recordId;
        this.visitId = visitId;
        this.scenicId = scenicId;
        this.enterTime = enterTime;
    }

    public static class CurrentSpot {
        public Long spotId;
        public String scenicId;
        public String spotName;
        public String enterTime;

        public CurrentSpot() {
        }

        public CurrentSpot(Long spotId, String scenicId, String spotName, String enterTime) {
            this.spotId = spotId;
            this.scenicId = scenicId;
            this.spotName = spotName;
            this.enterTime = enterTime;
        }
    }
}
