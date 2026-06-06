package com.scenic.ai.modules.app.route.entity;

import java.math.BigDecimal;

public class TouristRoutePlan {
    public Long id;
    public String planNo;
    public String userId;
    public String sessionId;
    public Long visitId;
    public Long areaId;
    public String routeName;
    public BigDecimal startLongitude;
    public BigDecimal startLatitude;
    public BigDecimal endLongitude;
    public BigDecimal endLatitude;
    public BigDecimal totalDistanceM;
    public Integer estimatedDurationMin;
    public String preferenceSnapshot;
    public String reason;
    public String rawResponseJson;
    public String planStatus;
}
