package com.scenic.ai.modules.app.visit.dto;

public class SpotLeaveResponse {

    public Long visitId;
    public String scenicId;
    public Long spotId;
    public String spotName;
    public Integer staySeconds;
    public Boolean success;

    public SpotLeaveResponse() {
    }

    public SpotLeaveResponse(Long visitId, String scenicId, Integer staySeconds) {
        this.visitId = visitId;
        this.scenicId = scenicId;
        this.staySeconds = staySeconds;
        this.success = true;
    }
}
