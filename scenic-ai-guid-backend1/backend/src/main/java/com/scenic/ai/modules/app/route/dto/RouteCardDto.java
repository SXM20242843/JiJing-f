package com.scenic.ai.modules.app.route.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteCardDto {

    public String type = "route";
    public String mapAction = "show_route_card";
    @JsonAlias({"schema_version", "schemaVersion"})
    @JsonProperty("schemaVersion")
    public String schemaVersion;
    @JsonAlias({"route_mode", "routeMode"})
    @JsonProperty("route_mode")
    public String routeMode;
    @JsonAlias({"visit_status", "visitStatus"})
    @JsonProperty("visit_status")
    public String visitStatus;
    @JsonAlias({"is_official_template", "isOfficialTemplate"})
    @JsonProperty("is_official_template")
    public Boolean isOfficialTemplate;
    @JsonAlias({"should_show_route_card", "shouldShowRouteCard"})
    @JsonProperty("should_show_route_card")
    public Boolean shouldShowRouteCard;
    public String planId;
    public Long routePlanId;
    @JsonAlias({"route_id"})
    public String routeId;
    @JsonAlias({"route_name", "name"})
    public String routeName;
    public String title;
    public String reason;
    @JsonAlias({"recommend_reason", "recommendReason"})
    public String recommendReason;
    @JsonAlias({"profile_version", "profileVersion"})
    public Long profileVersion;
    @JsonAlias({"matched_tags", "matchedTags"})
    public List<Object> matchedTags = new ArrayList<>();
    public String summary;
    public BigDecimal totalDistanceM;
    public String distanceText;
    @JsonAlias({"estimated_duration_minutes", "estimatedDurationMinutes"})
    public Integer estimatedDurationMinutes;
    public Integer estimatedDurationMin;
    public String durationText;
    public Integer spotCount;
    public List<Object> spots = new ArrayList<>();
    @JsonAlias({"narration_focus", "narrationFocus"})
    public List<Object> narrationFocus = new ArrayList<>();
    @JsonAlias({"experience_points", "experiencePoints"})
    public List<Object> experiencePoints = new ArrayList<>();
    public List<Object> warnings = new ArrayList<>();
    @JsonAlias({"algorithm_version", "algorithmVersion"})
    public String algorithmVersion;
    @JsonAlias({"template_spot_sequence", "templateSpotSequence"})
    @JsonProperty("template_spot_sequence")
    public List<Object> templateSpotSequence = new ArrayList<>();
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
