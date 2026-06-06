package com.scenic.ai.modules.app.route.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class RouteRecommendRequest {

    public String user_id;
    public String userId;

    public String session_id;
    public String sessionId;

    public Long visit_id;
    public Long visitId;

    public String mode;
    public String guide_mode;
    public String guideMode;

    public Long area_id;
    public Long areaId;

    public String area_code;
    public String areaCode;

    public String park_id;
    public String parkId;
    public String park_code;
    public String parkCode;

    public String park_name;
    public String parkName;

    public String route_start_type;
    public String routeStartType;

    public String current_spot_id;
    public String currentSpotId;
    public String current_spot_name;
    public String currentSpotName;

    public String spot_id;
    public String spotId;
    public String spot_name;
    public String spotName;

    public String question;
    public String source_page;
    public String sourcePage;

    public BigDecimal start_longitude;
    public BigDecimal startLongitude;
    public BigDecimal start_latitude;
    public BigDecimal startLatitude;
    public BigDecimal end_longitude;
    public BigDecimal endLongitude;
    public BigDecimal end_latitude;
    public BigDecimal endLatitude;

    public Map<String, Object> preference_snapshot;
    public Map<String, Object> preferenceSnapshot;
    public List<String> preference_tags;
    public List<String> preferenceTags;

    public Map<String, Object> client_context;
    public Map<String, Object> clientContext;

    public Integer available_minutes;
    public Integer availableMinutes;
    public Object user_profile;
    public Object userProfile;
    public Object recent_behaviors;
    public Object recentBehaviors;

    public Map<String, Object> route_json;
    public Map<String, Object> routeJson;
    public String raw_response_json;
    public String rawResponseJson;

    public String getUserIdText() {
        if (user_id != null && !user_id.trim().isEmpty()) return user_id.trim();
        if (userId != null && !userId.trim().isEmpty()) return userId.trim();
        return "";
    }

    public String getSessionIdText() {
        if (session_id != null && !session_id.trim().isEmpty()) return session_id.trim();
        if (sessionId != null && !sessionId.trim().isEmpty()) return sessionId.trim();
        return "";
    }

    public Long getVisitIdValue() {
        return visit_id != null ? visit_id : visitId;
    }

    public String getModeText() {
        if (mode != null && !mode.trim().isEmpty()) return mode.trim();
        if (guide_mode != null && !guide_mode.trim().isEmpty()) return guide_mode.trim();
        if (guideMode != null && !guideMode.trim().isEmpty()) return guideMode.trim();
        return "";
    }

    public Long getAreaIdValue() {
        return area_id != null ? area_id : areaId;
    }

    public String getAreaCodeText() {
        if (area_code != null && !area_code.trim().isEmpty()) return area_code.trim();
        if (areaCode != null && !areaCode.trim().isEmpty()) return areaCode.trim();
        if (park_code != null && !park_code.trim().isEmpty()) return park_code.trim();
        if (parkCode != null && !parkCode.trim().isEmpty()) return parkCode.trim();
        return "";
    }

    public String getParkIdText() {
        if (park_id != null && !park_id.trim().isEmpty()) return park_id.trim();
        if (parkId != null && !parkId.trim().isEmpty()) return parkId.trim();
        if (park_code != null && !park_code.trim().isEmpty()) return park_code.trim();
        if (parkCode != null && !parkCode.trim().isEmpty()) return parkCode.trim();
        return getAreaCodeText();
    }

    public String getParkNameText() {
        if (park_name != null && !park_name.trim().isEmpty()) return park_name.trim();
        if (parkName != null && !parkName.trim().isEmpty()) return parkName.trim();
        return "";
    }

    public String getRouteStartTypeText() {
        if (route_start_type != null && !route_start_type.trim().isEmpty()) return route_start_type.trim();
        if (routeStartType != null && !routeStartType.trim().isEmpty()) return routeStartType.trim();
        return "";
    }

    public String getCurrentSpotIdText() {
        if (current_spot_id != null && !current_spot_id.trim().isEmpty()) return current_spot_id.trim();
        if (currentSpotId != null && !currentSpotId.trim().isEmpty()) return currentSpotId.trim();
        if (spot_id != null && !spot_id.trim().isEmpty()) return spot_id.trim();
        if (spotId != null && !spotId.trim().isEmpty()) return spotId.trim();
        return "";
    }

    public String getCurrentSpotNameText() {
        if (current_spot_name != null && !current_spot_name.trim().isEmpty()) return current_spot_name.trim();
        if (currentSpotName != null && !currentSpotName.trim().isEmpty()) return currentSpotName.trim();
        if (spot_name != null && !spot_name.trim().isEmpty()) return spot_name.trim();
        if (spotName != null && !spotName.trim().isEmpty()) return spotName.trim();
        return "";
    }

    public String getQuestionText() {
        return question == null ? "" : question.trim();
    }

    public String getSourcePageText() {
        if (source_page != null && !source_page.trim().isEmpty()) return source_page.trim();
        if (sourcePage != null && !sourcePage.trim().isEmpty()) return sourcePage.trim();
        return "ai_chat";
    }

    public BigDecimal getStartLongitudeValue() {
        return start_longitude != null ? start_longitude : startLongitude;
    }

    public BigDecimal getStartLatitudeValue() {
        return start_latitude != null ? start_latitude : startLatitude;
    }

    public BigDecimal getEndLongitudeValue() {
        return end_longitude != null ? end_longitude : endLongitude;
    }

    public BigDecimal getEndLatitudeValue() {
        return end_latitude != null ? end_latitude : endLatitude;
    }

    public Map<String, Object> getPreferenceSnapshotValue() {
        return preference_snapshot != null ? preference_snapshot : preferenceSnapshot;
    }

    public List<String> getPreferenceTagsValue() {
        return preference_tags != null ? preference_tags : preferenceTags;
    }

    public Map<String, Object> getClientContextValue() {
        return client_context != null ? client_context : clientContext;
    }

    public Integer getAvailableMinutesValue() {
        if (available_minutes != null) return available_minutes;
        if (availableMinutes != null) return availableMinutes;
        return readIntegerFromMap(getClientContextValue(), "available_minutes", "availableMinutes");
    }

    public Object getUserProfileValue() {
        if (user_profile != null) return user_profile;
        if (userProfile != null) return userProfile;
        return readObjectFromMap(getClientContextValue(), "user_profile", "userProfile");
    }

    public Object getRecentBehaviorsValue() {
        if (recent_behaviors != null) return recent_behaviors;
        if (recentBehaviors != null) return recentBehaviors;
        return readObjectFromMap(getClientContextValue(), "recent_behaviors", "recentBehaviors");
    }

    public Map<String, Object> getRouteJsonValue() {
        return route_json != null ? route_json : routeJson;
    }

    public String getRawResponseJsonText() {
        if (raw_response_json != null && !raw_response_json.trim().isEmpty()) return raw_response_json.trim();
        if (rawResponseJson != null && !rawResponseJson.trim().isEmpty()) return rawResponseJson.trim();
        return "";
    }

    private Object readObjectFromMap(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private Integer readIntegerFromMap(Map<String, Object> map, String... keys) {
        Object value = readObjectFromMap(map, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
