package com.scenic.ai.modules.app.visit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VisitReportResponse {

    public Long visitId;
    public String visitNo;
    public String userId;
    public Long areaId;
    public String areaName;
    public String parkId;
    public String parkName;
    public String parkDesc;
    public String coverImage;
    public String groupSize;
    public String travelPeopleCount;
    public String travelType;
    public String visitPreference;
    public String travelPreference;
    public String estimatedDuration;
    public String startTime;
    public String endTime;
    public Integer stayDuration;
    public String stayDurationText;
    public Integer durationSeconds;
    public String durationText;
    public String visitStatus;
    public Integer spotCount;
    public Integer visitedSpotCount;
    public Integer aiQuestionCount;
    public Integer chatCount;
    public Integer favoriteCount;
    public List<VisitReportSpotDto> visitedSpots = new ArrayList<>();
    public Map<String, Object> consumptionSummary = new LinkedHashMap<>();
    public Map<String, Object> behaviorSummary = new LinkedHashMap<>();
    public List<VisitReportSpotDto> spots = new ArrayList<>();
    public BigDecimal satisfaction;
    public String comment;
    public Integer recommend;
    public String consumeStatus;
    public BigDecimal ticketCost;
    public BigDecimal foodCost;
    public BigDecimal shoppingCost;
    public BigDecimal transportCost;
    public BigDecimal entertainmentCost;
    public BigDecimal totalCost;
    public List<RecommendParkDto> recommendParks = new ArrayList<>();

    @JsonIgnore
    public LocalDateTime rawStartTime;

    @JsonIgnore
    public LocalDateTime rawEndTime;

    @JsonIgnore
    public Integer sessionSatisfaction;

    @JsonIgnore
    public String sessionComment;
}
