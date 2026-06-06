package com.scenic.ai.modules.app.user.dto;

import java.math.BigDecimal;
import java.util.Map;

public class BehaviorEventRequest {

    public String event_id;
    public String eventId;

    public String user_id;
    public String userId;

    public String visitor_id;
    public String visitorId;

    public String session_id;
    public String sessionId;

    public Object visit_id;
    public Object visitId;

    public Object area_id;
    public Object areaId;

    public Object spot_id;
    public Object spotId;

    public Object facility_id;
    public Object facilityId;

    public String area_code;
    public String areaCode;

    public String scene_code;
    public String sceneCode;

    public String entity_type;
    public String entityType;

    public String entity_id;
    public String entityId;

    public String plan_id;
    public String planId;
    public String route_plan_id;
    public String routePlanId;

    public String route_name;
    public String routeName;

    public String park_name;
    public String parkName;

    public String spot_name;
    public String spotName;

    public String location_source;
    public String locationSource;

    public String trigger;

    public Boolean demo_mode;
    public Boolean demoMode;

    public String event_type;
    public String eventType;

    public String event_name;
    public String eventName;

    public String source;
    public String source_page;
    public String sourcePage;

    public String keyword;
    public String content;

    public BigDecimal score;

    public Integer duration_seconds;
    public Integer durationSeconds;

    public BigDecimal longitude;
    public BigDecimal latitude;

    public BigDecimal gps_accuracy_m;
    public BigDecimal gpsAccuracyM;

    public String device_id;
    public String deviceId;

    public String client_type;
    public String clientType;

    public Map<String, Object> extra;
    public String extra_json;
    public String extraJson;

    public String getEventIdText() {
        if (event_id != null && !event_id.trim().isEmpty()) return event_id.trim();
        if (eventId != null && !eventId.trim().isEmpty()) return eventId.trim();
        return "";
    }

    public String getUserIdText() {
        if (user_id != null && !user_id.trim().isEmpty()) return user_id.trim();
        if (userId != null && !userId.trim().isEmpty()) return userId.trim();
        if (visitor_id != null && !visitor_id.trim().isEmpty()) return visitor_id.trim();
        if (visitorId != null && !visitorId.trim().isEmpty()) return visitorId.trim();
        return "anonymous";
    }

    public String getVisitorIdText() {
        if (visitor_id != null && !visitor_id.trim().isEmpty()) return visitor_id.trim();
        if (visitorId != null && !visitorId.trim().isEmpty()) return visitorId.trim();
        return "";
    }

    public String getSessionIdText() {
        if (session_id != null && !session_id.trim().isEmpty()) return session_id.trim();
        if (sessionId != null && !sessionId.trim().isEmpty()) return sessionId.trim();
        return "";
    }

    public Long getVisitIdValue() {
        return parseLongOrNull(visit_id != null ? visit_id : visitId);
    }

    public Long getAreaIdValue() {
        return parseLongOrNull(area_id != null ? area_id : areaId);
    }

    public Long getSpotIdValue() {
        return parseLongOrNull(spot_id != null ? spot_id : spotId);
    }

    public Long getFacilityIdValue() {
        return parseLongOrNull(facility_id != null ? facility_id : facilityId);
    }

    public String getAreaCodeText() {
        if (area_code != null && !area_code.trim().isEmpty()) return area_code.trim();
        if (areaCode != null && !areaCode.trim().isEmpty()) return areaCode.trim();
        String areaIdText = firstNotBlankObject(area_id, areaId);
        if (!areaIdText.matches("\\d+")) return areaIdText;
        return "";
    }

    public String getSceneCodeText() {
        if (scene_code != null && !scene_code.trim().isEmpty()) return scene_code.trim();
        if (sceneCode != null && !sceneCode.trim().isEmpty()) return sceneCode.trim();
        String spotIdText = firstNotBlankObject(spot_id, spotId);
        if (!spotIdText.matches("\\d+")) return spotIdText;
        return "";
    }

    public String getEntityTypeText() {
        if (entity_type != null && !entity_type.trim().isEmpty()) return entity_type.trim();
        if (entityType != null && !entityType.trim().isEmpty()) return entityType.trim();
        return "OTHER";
    }

    public String getEntityIdText() {
        if (entity_id != null && !entity_id.trim().isEmpty()) return entity_id.trim();
        if (entityId != null && !entityId.trim().isEmpty()) return entityId.trim();
        String sceneCodeText = getSceneCodeText();
        if (!sceneCodeText.isEmpty()) return sceneCodeText;
        String areaCodeText = getAreaCodeText();
        if (!areaCodeText.isEmpty()) return areaCodeText;
        return "";
    }

    public String getEventTypeText() {
        if (event_type != null && !event_type.trim().isEmpty()) return normalizeEventType(event_type);
        if (eventType != null && !eventType.trim().isEmpty()) return normalizeEventType(eventType);
        return "";
    }

    public String getEventNameText() {
        if (event_name != null && !event_name.trim().isEmpty()) return event_name.trim();
        if (eventName != null && !eventName.trim().isEmpty()) return eventName.trim();
        return getEventTypeText();
    }

    public String getSourcePageText() {
        if (source_page != null && !source_page.trim().isEmpty()) return source_page.trim();
        if (sourcePage != null && !sourcePage.trim().isEmpty()) return sourcePage.trim();
        if (source != null && !source.trim().isEmpty()) return source.trim();
        return "";
    }

    public String getPlanIdText() {
        if (plan_id != null && !plan_id.trim().isEmpty()) return plan_id.trim();
        if (planId != null && !planId.trim().isEmpty()) return planId.trim();
        if (route_plan_id != null && !route_plan_id.trim().isEmpty()) return route_plan_id.trim();
        if (routePlanId != null && !routePlanId.trim().isEmpty()) return routePlanId.trim();
        return "";
    }

    public String getRouteNameText() {
        if (route_name != null && !route_name.trim().isEmpty()) return route_name.trim();
        if (routeName != null && !routeName.trim().isEmpty()) return routeName.trim();
        return "";
    }

    public String getParkNameText() {
        if (park_name != null && !park_name.trim().isEmpty()) return park_name.trim();
        if (parkName != null && !parkName.trim().isEmpty()) return parkName.trim();
        return "";
    }

    public String getSpotNameText() {
        if (spot_name != null && !spot_name.trim().isEmpty()) return spot_name.trim();
        if (spotName != null && !spotName.trim().isEmpty()) return spotName.trim();
        return "";
    }

    public Integer getDurationSecondsValue() {
        return duration_seconds != null ? duration_seconds : durationSeconds;
    }

    public BigDecimal getGpsAccuracyValue() {
        return gps_accuracy_m != null ? gps_accuracy_m : gpsAccuracyM;
    }

    public String getDeviceIdText() {
        if (device_id != null && !device_id.trim().isEmpty()) return device_id.trim();
        if (deviceId != null && !deviceId.trim().isEmpty()) return deviceId.trim();
        return getVisitorIdText();
    }

    public String getClientTypeText() {
        if (client_type != null && !client_type.trim().isEmpty()) return client_type.trim();
        if (clientType != null && !clientType.trim().isEmpty()) return clientType.trim();
        return "APP";
    }

    private String normalizeEventType(String value) {
        String trimmed = value == null ? "" : value.trim();
        String lower = trimmed.toLowerCase();
        if ("route_view".equals(lower)
                || "map_card_show".equals(lower)
                || "navigation_start".equals(lower)
                || "route_spot_click".equals(lower)
                || "map_card_expand".equals(lower)
                || "map_card_close".equals(lower)
                || "spot_enter".equals(lower)
                || "spot_leave".equals(lower)
                || "visit_start".equals(lower)
                || "gps_enter_scenic".equals(lower)
                || "gps_leave_scenic".equals(lower)
                || "visit_end".equals(lower)) {
            return lower;
        }
        return trimmed.toUpperCase();
    }

    public String getLocationSourceText() {
        if (location_source != null && !location_source.trim().isEmpty()) return location_source.trim();
        if (locationSource != null && !locationSource.trim().isEmpty()) return locationSource.trim();
        return "";
    }

    public String getTriggerText() {
        return trigger == null ? "" : trigger.trim();
    }

    public Boolean getDemoModeValue() {
        return demo_mode != null ? demo_mode : demoMode;
    }

    private Long parseLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNotBlankObject(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }
}
