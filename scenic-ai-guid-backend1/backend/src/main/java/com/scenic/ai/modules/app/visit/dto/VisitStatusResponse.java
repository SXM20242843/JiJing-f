package com.scenic.ai.modules.app.visit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Map;

public class VisitStatusResponse {

    public Boolean hasRunningVisit;
    public String visitId;
    public String userId;
    public String areaId;
    public String areaName;
    public String status;
    public String startTime;
    public String startedAt;
    public String endTime;
    public Integer durationSeconds;
    public Boolean reportReady;
    public Boolean hasLastReport;
    public String lastReportVisitId;
    public String lastReportAreaName;
    public String lastFinishedAt;
    public Map<String, Object> userInfo;

    @JsonIgnore
    public LocalDateTime rawStartTime;

    @JsonIgnore
    public LocalDateTime rawEndTime;

    @JsonIgnore
    public String rawStatus;
}
