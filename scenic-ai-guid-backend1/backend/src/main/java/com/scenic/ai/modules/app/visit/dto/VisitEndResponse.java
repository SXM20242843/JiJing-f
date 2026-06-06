package com.scenic.ai.modules.app.visit.dto;

public class VisitEndResponse {

    public Long visitId;
    public String startTime;
    public String endTime;
    public Integer totalDurationSeconds;
    public Integer durationSeconds;
    public String status;
    public Boolean reportReady;
    public Long reportVisitId;

    public VisitEndResponse() {
    }

    public VisitEndResponse(
            Long visitId,
            String startTime,
            String endTime,
            Integer totalDurationSeconds,
            String status
    ) {
        this.visitId = visitId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalDurationSeconds = totalDurationSeconds;
        this.durationSeconds = totalDurationSeconds;
        this.status = status;
        this.reportReady = isReportReady(status, endTime);
        this.reportVisitId = visitId;
    }

    private Boolean isReportReady(String status, String endTime) {
        if (endTime != null && !endTime.trim().isEmpty()) {
            return true;
        }

        if (status == null) {
            return false;
        }

        String value = status.trim().toUpperCase();
        return "ENDED".equals(value) || "FINISHED".equals(value) || "COMPLETED".equals(value);
    }
}
