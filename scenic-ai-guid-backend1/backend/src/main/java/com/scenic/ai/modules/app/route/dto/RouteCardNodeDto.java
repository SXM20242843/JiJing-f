package com.scenic.ai.modules.app.route.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

public class RouteCardNodeDto {

    @JsonAlias({"sortOrder", "sort_order"})
    public Integer order;
    public Integer nodeOrder;
    public String nodeType;
    public String scenicId;
    public Long spotId;
    public Long facilityId;
    public String scenicName;
    public String spotName;
    @JsonAlias({"displayName", "display_name"})
    public String displayName;
    @JsonAlias({"sceneCode", "scene_code"})
    public String sceneCode;
    @JsonAlias({"nodeName", "node_name"})
    public String nodeName;
    public BigDecimal latitude;
    public BigDecimal longitude;
    public String guideText;
    public Integer recommendedStayMin;
    @JsonAlias({"distanceFromPreviousMeters", "distance_from_previous_meters"})
    public Integer distanceFromPreviousMeters;
    @JsonAlias({"estimatedWalkMinutes", "estimated_walk_minutes"})
    public Integer estimatedWalkMinutes;
    @JsonAlias({"recommendedStayMinutes", "recommended_stay_minutes"})
    public Integer recommendedStayMinutes;
}
