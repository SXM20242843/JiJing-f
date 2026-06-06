package com.scenic.ai.modules.app.visit.dto;

public class VisitStartResponse {

    public Long visitId;
    public String visitNo;
    public String userId;
    public String parkId;
    public String parkName;
    public Long areaId;
    public String areaName;
    public String startTime;
    public String status;

    public VisitStartResponse() {
    }

    public VisitStartResponse(
            Long visitId,
            String visitNo,
            String userId,
            String parkId,
            String parkName,
            Long areaId,
            String startTime,
            String status
    ) {
        this.visitId = visitId;
        this.visitNo = visitNo;
        this.userId = userId;
        this.parkId = parkId;
        this.parkName = parkName;
        this.areaId = areaId;
        this.areaName = parkName;
        this.startTime = startTime;
        this.status = status;
    }
}
