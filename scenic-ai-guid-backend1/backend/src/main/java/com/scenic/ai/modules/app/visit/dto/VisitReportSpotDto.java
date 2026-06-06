package com.scenic.ai.modules.app.visit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

public class VisitReportSpotDto {

    public String scenicId;
    public String scenicName;
    public String spotId;
    public String spotName;
    public String enterTime;
    public String leaveTime;
    public Integer staySeconds;
    public String stayDurationText;
    public Integer durationSeconds;
    public String durationText;

    @JsonIgnore
    public LocalDateTime rawEnterTime;

    @JsonIgnore
    public LocalDateTime rawLeaveTime;
}
