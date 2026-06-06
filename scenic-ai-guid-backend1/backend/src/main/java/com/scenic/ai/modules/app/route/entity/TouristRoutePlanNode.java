package com.scenic.ai.modules.app.route.entity;

import java.math.BigDecimal;

public class TouristRoutePlanNode {
    public Long id;
    public Long planId;
    public String nodeType;
    public Long spotId;
    public Long facilityId;
    public String nodeName;
    public BigDecimal longitude;
    public BigDecimal latitude;
    public Integer sortOrder;
    public BigDecimal distanceFromPrevM;
    public Integer estimatedWalkMin;
    public Integer recommendedStayMin;
    public String guideText;
}
