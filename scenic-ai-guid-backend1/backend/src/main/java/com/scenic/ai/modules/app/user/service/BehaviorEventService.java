package com.scenic.ai.modules.app.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.mapper.BehaviorEventMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BehaviorEventService {

    private final BehaviorEventMapper behaviorEventMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BehaviorEventService(BehaviorEventMapper behaviorEventMapper) {
        this.behaviorEventMapper = behaviorEventMapper;
    }

    public String addBehaviorEvent(BehaviorEventRequest request) {
        return addBehaviorEvent(request, null);
    }

    public String addBehaviorEvent(BehaviorEventRequest request, String requiredUserId) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        String eventType = request.getEventTypeText();

        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("event_type 不能为空");
        }

        String eventId = request.getEventIdText();
        if (eventId.isEmpty()) {
            eventId = generateEventId();
        }

        String userId = firstNotBlank(requiredUserId, request.getUserIdText());
        if (isTemporaryUserId(userId)) {
            throw new IllegalArgumentException("请先登录后再上报行为");
        }

        String sessionId = request.getSessionIdText();
        Long visitId = request.getVisitIdValue();

        Long areaId = request.getAreaIdValue();
        Long spotId = request.getSpotIdValue();

        String areaCode = request.getAreaCodeText();
        String sceneCode = request.getSceneCodeText();

        if (areaId == null && !areaCode.isEmpty()) {
            areaId = behaviorEventMapper.selectAreaIdByAreaCode(areaCode);
        }

        if (spotId == null && !sceneCode.isEmpty()) {
            spotId = behaviorEventMapper.selectSpotIdBySceneCode(sceneCode);
        }

        String extraJson = buildExtraJson(request, areaCode, sceneCode);

        behaviorEventMapper.insertBehaviorEvent(
                eventId,
                userId,
                sessionId,
                visitId,
                areaId,
                spotId,
                request.getFacilityIdValue(),
                request.getEntityTypeText(),
                request.getEntityIdText(),
                eventType,
                request.getEventNameText(),
                request.getSourcePageText(),
                request.keyword,
                request.content,
                request.score,
                request.getDurationSecondsValue(),
                request.longitude,
                request.latitude,
                request.getGpsAccuracyValue(),
                request.getDeviceIdText(),
                request.getClientTypeText(),
                extraJson
        );

        return eventId;
    }

    private String buildExtraJson(BehaviorEventRequest request, String areaCode, String sceneCode) {
        try {
            Map<String, Object> extraMap = new HashMap<>();

            if (request.extra != null) {
                extraMap.putAll(request.extra);
            }

            if (request.extra_json != null && !request.extra_json.trim().isEmpty()) {
                extraMap.put("raw_extra_json", request.extra_json);
            }

            if (request.extraJson != null && !request.extraJson.trim().isEmpty()) {
                extraMap.put("raw_extra_json", request.extraJson);
            }

            if (request.getVisitorIdText() != null && !request.getVisitorIdText().isEmpty()) {
                extraMap.put("visitor_id", request.getVisitorIdText());
            }

            if (!request.getPlanIdText().isEmpty()) {
                extraMap.put("planId", request.getPlanIdText());
            }

            if (!request.getRouteNameText().isEmpty()) {
                extraMap.put("routeName", request.getRouteNameText());
            }

            if (!request.getParkNameText().isEmpty()) {
                extraMap.put("parkName", request.getParkNameText());
            }

            if (!request.getSpotNameText().isEmpty()) {
                extraMap.put("spotName", request.getSpotNameText());
            }

            if (request.source != null && !request.source.trim().isEmpty()) {
                extraMap.put("source", request.source.trim());
            }

            if (!request.getLocationSourceText().isEmpty()) {
                extraMap.put("locationSource", request.getLocationSourceText());
            }

            if (!request.getTriggerText().isEmpty()) {
                extraMap.put("trigger", request.getTriggerText());
            }

            if (request.getDemoModeValue() != null) {
                extraMap.put("demoMode", request.getDemoModeValue());
            }

            if (areaCode != null && !areaCode.isEmpty()) {
                extraMap.put("area_code", areaCode);
            }

            if (sceneCode != null && !sceneCode.isEmpty()) {
                extraMap.put("scene_code", sceneCode);
            }

            return objectMapper.writeValueAsString(extraMap);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String generateEventId() {
        return "event_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private boolean isTemporaryUserId(String userId) {
        if (userId == null) {
            return true;
        }

        String value = userId.trim().toLowerCase();
        return value.isEmpty()
                || "anonymous".equals(value)
                || value.startsWith("visitor_")
                || value.startsWith("android-live2d-");
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }
}
