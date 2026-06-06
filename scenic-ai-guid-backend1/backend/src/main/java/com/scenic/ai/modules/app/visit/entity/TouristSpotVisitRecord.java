package com.scenic.ai.modules.app.visit.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TouristSpotVisitRecord {

    public Long id;
    public String userId;
    public Long visitId;
    public Long areaId;
    public Long spotId;
    public String frontendScenicId;
    public String frontendScenicName;
    public LocalDateTime enterTime;
    public LocalDateTime leaveTime;
    public Integer staySeconds;
    public BigDecimal enterLongitude;
    public BigDecimal enterLatitude;
    public BigDecimal leaveLongitude;
    public BigDecimal leaveLatitude;
    public String sourceType;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
