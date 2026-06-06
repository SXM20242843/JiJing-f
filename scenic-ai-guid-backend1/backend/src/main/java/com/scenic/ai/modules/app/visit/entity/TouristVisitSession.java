package com.scenic.ai.modules.app.visit.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TouristVisitSession {

    public Long id;
    public String visitNo;
    public String userId;
    public String chatSessionId;
    public Long areaId;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public BigDecimal startLongitude;
    public BigDecimal startLatitude;
    public BigDecimal endLongitude;
    public BigDecimal endLatitude;
    public Integer totalDurationSeconds;
    public String visitStatus;
    public String sourceType;
    public String groupSize;
    public String travelType;
    public String visitPreference;
    public String consumeStatus;
    public BigDecimal ticketCost;
    public BigDecimal foodCost;
    public BigDecimal shoppingCost;
    public BigDecimal transportCost;
    public BigDecimal entertainmentCost;
    public BigDecimal totalCost;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
