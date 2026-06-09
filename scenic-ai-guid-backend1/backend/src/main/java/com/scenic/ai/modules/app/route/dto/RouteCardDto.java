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

    /**
     * 真实可绘制路线折线。
     * 优先由小后端通过高德 WebService 步行规划生成，Android 端直接画这条线。
     */
    public List<Map<String, BigDecimal>> polyline = new ArrayList<>();

    /**
     * 兼容字段：真实道路折线。Android/前端可优先读取 routePolyline/mapPolyline/roadPolyline。
     */
    public List<Map<String, BigDecimal>> routePolyline = new ArrayList<>();
    public List<Map<String, BigDecimal>> mapPolyline = new ArrayList<>();
    public List<Map<String, BigDecimal>> roadPolyline = new ArrayList<>();

    /**
     * 景点节点直线，仅用于兜底或调试，避免和真实道路 polyline 混淆。
     */
    public List<Map<String, BigDecimal>> nodePolyline = new ArrayList<>();

    public Boolean routeMapReady;
    public Boolean mapReady;
    public Boolean partialFallback;
    public String routeMapMessage;
    public String routeMapSource;
}
