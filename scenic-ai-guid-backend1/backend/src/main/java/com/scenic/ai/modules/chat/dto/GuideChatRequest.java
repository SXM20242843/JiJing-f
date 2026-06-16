package com.scenic.ai.modules.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class GuideChatRequest {

    private static final ObjectMapper FLEXIBLE_OBJECT_MAPPER = new ObjectMapper();

    @JsonAlias({"session_id"})
    private String sessionId;

    @JsonAlias({"user_id"})
    private String userId;

    @JsonAlias({"visit_id"})
    private String visitId;

    @JsonAlias({"visit_no", "visitNo"})
    private String visitNo;

    @JsonAlias({"mode", "guide_mode", "guideMode"})
    private String mode;

    @JsonAlias({"park_id"})
    private String parkId;

    @JsonAlias({"park_name"})
    private String parkName;

    @JsonAlias({"scenic_id"})
    private String scenicId;

    @JsonAlias({"login_user_id", "loginUserId"})
    private String loginUserId;

    @JsonAlias({"visitor_id", "visitorId"})
    private String visitorId;

    @JsonAlias({"conversation_id"})
    private String conversationId;

    @NotBlank(message = "问题不能为空")
    private String question;

    @JsonAlias({"raw_question", "rawQuestion"})
    private String rawQuestion;

    @JsonAlias({"scenic_name"})
    private String scenicName;

    @JsonAlias({"need_voice"})
    private Boolean needVoice = false;

    @JsonAlias({"area_code"})
    private String areaCode;

    @JsonAlias({"area_name"})
    private String areaName;

    @JsonAlias({"area_id"})
    private Long areaId;

    @JsonAlias({"scene_code", "sceneCode"})
    private String sceneCode;

    @JsonAlias({"scene_name", "sceneName"})
    private String sceneName;

    @JsonAlias({"current_spot_id"})
    private String currentSpotId;

    @JsonAlias({"current_spot_name"})
    private String currentSpotName;

    @JsonAlias({"spot_id", "spotId"})
    private String spotId;

    @JsonAlias({"spot_name", "spotName"})
    private String spotName;

    @JsonAlias({"input_type"})
    private String inputType = "text";

    private String voice = "zhitian_emo";

    @JsonAlias({"enable_tts"})
    private Boolean enableTts;

    @JsonAlias({"enable_context"})
    private Boolean enableContext = true;

    @JsonAlias({"client_context"})
    private Map<String, Object> clientContext;

    @JsonAlias({"route", "is_route", "isRoute"})
    private Boolean route;

    private Object routePayload;

    @JsonAlias({"route_intent", "routeIntent"})
    private Boolean routeIntent;

    @JsonAlias({"suppress_route", "suppressRoute"})
    private Boolean suppressRoute;

    @JsonAlias({"request_type", "requestType"})
    private String requestType;

    @JsonAlias({"route_start_type", "routeStartType"})
    private String routeStartType;

    @JsonAlias({"visit_status", "visitStatus"})
    private String visitStatus;

    @JsonAlias({"is_inside_area", "isInsideArea"})
    private Boolean isInsideArea;

    private Object context;

    @JsonAlias({"location_context", "locationContext"})
    private Object locationContext;

    @JsonAlias({"network_context", "networkContext"})
    private Object networkContext;

    private Object options;

    @JsonAlias({"short_term_context", "shortTermContext"})
    private Object shortTermContext;

    @JsonAlias({"preference_tags", "preferenceTags"})
    private List<String> preferenceTags;

    @JsonAlias({"available_minutes", "availableMinutes"})
    private Integer availableMinutes;

    @JsonAlias({"remaining_minutes", "remainingMinutes"})
    private Integer remainingMinutes;

    @JsonAlias({"travel_party", "travelParty"})
    private Object travelParty;

    @JsonAlias({"user_profile", "userProfile"})
    private Object userProfile;

    @JsonAlias({"profile"})
    private Object profile;

    @JsonAlias({"digital_human_config", "digitalHumanConfig"})
    private Object digitalHumanConfig;

    @JsonAlias({"voice_id", "voiceId"})
    private String voiceId;

    @JsonAlias({"avatar_id", "avatarId"})
    private String avatarId;

    @JsonAlias({"recent_behaviors", "recentBehaviors"})
    private Object recentBehaviors;

    private Object location;

    @JsonAlias({"accuracy_meters", "accuracyMeters", "gps_accuracy_m", "gpsAccuracyM"})
    private BigDecimal accuracyMeters;

    @JsonAlias({"current_spot", "currentSpot"})
    private Object currentSpot;

    @JsonAlias({"profile_snapshot", "profileSnapshot"})
    private Object profileSnapshot;

    @JsonAlias({"current_visit_context", "currentVisitContext"})
    private Object currentVisitContext;

    // ========== 新增三个表单字段 ==========
    @JsonAlias({"group_size"})
    private String groupSize;

    @JsonAlias({"travel_type"})
    private String travelType;

    @JsonAlias({"visit_preference"})
    private String visitPreference;
    // ====================================

    // 可选：从请求中获取经纬度（如果需要）
    @JsonAlias({"lat", "current_latitude", "currentLatitude"})
    private BigDecimal latitude;

    @JsonAlias({"lng", "lon", "current_longitude", "currentLongitude"})
    private BigDecimal longitude;

    public String getEffectiveUserId() {
        String value = firstNotBlank(userId, loginUserId, visitorId, sessionId);
        return value == null ? "" : value;
    }

    public String getEffectiveSessionId() {
        String value = firstNotBlank(sessionId, conversationId);
        return value == null ? "" : value;
    }

    public String getEffectiveConversationId() {
        String value = firstNotBlank(conversationId, sessionId);
        return value == null ? "" : value;
    }

    public String getEffectiveCurrentSpotId() {
        String value = firstNotBlank(currentSpotId, spotId, scenicId, sceneCode);
        return value == null ? "" : value;
    }

    public String getEffectiveCurrentSpotName() {
        String value = firstNotBlank(currentSpotName, spotName, scenicName, sceneName);
        return value == null ? "" : value;
    }

    public String getEffectiveParkId() {
        String value = firstNotBlank(parkId, areaCode);
        return value == null ? "" : value;
    }

    public String getEffectiveParkName() {
        String value = firstNotBlank(parkName, areaName);
        return value == null ? "" : value;
    }

    public String getEffectiveScenicId() {
        String value = firstNotBlank(scenicId, sceneCode, currentSpotId, spotId);
        return value == null ? "" : value;
    }

    public String getEffectiveScenicName() {
        String value = firstNotBlank(scenicName, currentSpotName, spotName, sceneName, areaName);
        return value == null ? "" : value;
    }

    public String getEffectiveInputType() {
        String value = firstNotBlank(inputType);
        return value == null || value.isEmpty() ? "text" : value;
    }

    public String getEffectiveVoice() {
        String value = firstNotBlank(voiceId, voice);
        return value == null || value.isEmpty() ? "zhitian_emo" : value;
    }

    public Object getEffectiveProfile() {
        return profile != null ? profile : userProfile;
    }

    public Boolean getEffectiveEnableTts() {
        if (enableTts != null) return enableTts;
        if (needVoice != null) return needVoice;
        return true;
    }

    public Boolean getEffectiveEnableContext() {
        return enableContext == null ? true : enableContext;
    }

    public void setAreaId(Object value) {
        if (value instanceof Number number) {
            this.areaId = number.longValue();
            return;
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            this.areaId = null;
            return;
        }

        String text = String.valueOf(value).trim();
        try {
            this.areaId = Long.parseLong(text);
        } catch (NumberFormatException e) {
            this.areaId = null;
            if (areaCode == null || areaCode.trim().isEmpty()) {
                areaCode = text;
            }
            if (parkId == null || parkId.trim().isEmpty()) {
                parkId = text;
            }
        }
    }

    public void setClientContext(Object value) {
        this.clientContext = toFlexibleMap(value);
    }

    public void setRoute(Object value) {
        this.routePayload = normalizeFlexibleObject(value);
        this.route = readRouteEnabled(value);
    }

    private Boolean readRouteEnabled(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        Object normalized = normalizeFlexibleObject(value);
        if (normalized instanceof Map<?, ?> rawMap) {
            Object enabled = firstMapValue(rawMap, "enabled", "route", "isRoute", "is_route");
            if (enabled != null) {
                return readBooleanValue(enabled);
            }
            return null;
        }
        return readBooleanValue(normalized);
    }

    private Boolean readBooleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text) || "y".equals(text) || "是".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text) || "n".equals(text) || "否".equals(text)) {
            return false;
        }
        return null;
    }

    private Object firstMapValue(Map<?, ?> map, String... keys) {
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

    private Map<String, Object> toFlexibleMap(Object value) {
        Object normalized = normalizeFlexibleObject(value);
        if (normalized instanceof Map<?, ?> rawMap) {
            return FLEXIBLE_OBJECT_MAPPER.convertValue(rawMap, new TypeReference<Map<String, Object>>() {
            });
        }
        if (normalized == null) {
            return null;
        }
        Map<String, Object> fallback = new java.util.LinkedHashMap<>();
        fallback.put("_raw", normalized);
        return fallback;
    }

    private Object normalizeFlexibleObject(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            return value;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                return FLEXIBLE_OBJECT_MAPPER.readValue(trimmed, Object.class);
            } catch (Exception ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
