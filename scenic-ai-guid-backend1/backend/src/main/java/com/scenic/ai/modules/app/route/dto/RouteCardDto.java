package com.scenic.ai.modules.app.route.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RouteCardDto {

    public String type = "route";
    public String mapAction = "show_route_card";
    public String planId;
    public Long routePlanId;
    public String routeName;
    public String title;
    public String reason;
    public String summary;
    public BigDecimal totalDistanceM;
    public String distanceText;
    public Integer estimatedDurationMin;
    public String durationText;
    public Integer spotCount;
    public List<RouteCardNodeDto> nodes = new ArrayList<>();
    public List<Map<String, BigDecimal>> polyline = new ArrayList<>();
}
