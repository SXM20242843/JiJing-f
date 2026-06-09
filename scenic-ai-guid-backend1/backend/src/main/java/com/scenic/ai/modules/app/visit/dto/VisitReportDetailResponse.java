package com.scenic.ai.modules.app.visit.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VisitReportDetailResponse {

    public String visitId;
    public String areaId;
    public String areaName;
    public String parkId;
    public String parkName;
    public String startTime;
    public String endTime;
    public Integer duration;
    public Integer durationMinutes;
    public Integer durationSeconds;
    public String durationText;
    public Integer spotCount;
    public Integer visitedSpotCount;
    public String groupSize;
    public String travelPeopleCount;
    public String travelType;
    public String visitPreference;
    public String travelPreference;
    public String estimatedDuration;
    public Integer aiQuestionCount;
    public Integer chatCount;
    public Integer questionCount;
    public Integer favoriteCount;
    public TravelInfo travelInfo = new TravelInfo();
    public List<SpotStayItem> spotStayList = new ArrayList<>();
    public List<SpotStayItem> visitedSpots = new ArrayList<>();
    public List<Object> routePlans = new ArrayList<>();
    public Map<String, Object> behaviorSummary = new LinkedHashMap<>();
    public Map<String, Object> consumptionSummary = new LinkedHashMap<>();
    public String consumeStatus;
    public BigDecimal ticketCost;
    public BigDecimal foodCost;
    public BigDecimal shoppingCost;
    public BigDecimal transportCost;
    public BigDecimal entertainmentCost;
    public BigDecimal totalCost;
    public List<ConsumeItem> consumeList = new ArrayList<>();
    public List<RecommendParkDto> recommendationSimilarScenic = new ArrayList<>();
    public Boolean profileUpdated = false;
    public String summary;

    public static class TravelInfo {
        public String peopleCount;
        public String travelType;
        public String preference;
    }

    public static class SpotStayItem {
        public String spotId;
        public String spotName;
        public String enterTime;
        public String leaveTime;
        public String startTime;
        public String endTime;
        public Integer durationSeconds;
        public String durationText;
    }

    public static class ConsumeItem {
        public String consumptionType;
        public BigDecimal amount;
        public String paymentId;
        public String merchantName;
        public String locationId;
        public String payTime;
    }
}
