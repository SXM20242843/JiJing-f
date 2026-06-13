package com.scenic.ai.modules.app.location.service;

import com.scenic.ai.modules.app.location.dto.NfcCheckinRequest;
import com.scenic.ai.modules.app.location.dto.NfcCheckinResponse;
import com.scenic.ai.modules.app.location.dto.NfcMarkerDto;
import com.scenic.ai.modules.app.location.mapper.NfcMarkerMapper;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.service.AppUserService;
import com.scenic.ai.modules.app.user.service.BehaviorEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NFC 打点签到服务。
 *
 * 优先查询 scenic_nfc_marker 表；查不到时 fallback 到演示映射。
 */
@Slf4j
@Service
public class NfcCheckinService {

    private final NfcMarkerMapper nfcMarkerMapper;
    private final BehaviorEventService behaviorEventService;
    private final AppUserService appUserService;

    public NfcCheckinService(NfcMarkerMapper nfcMarkerMapper,
                             BehaviorEventService behaviorEventService,
                             AppUserService appUserService) {
        this.nfcMarkerMapper = nfcMarkerMapper;
        this.behaviorEventService = behaviorEventService;
        this.appUserService = appUserService;
    }

    // ==================== 演示 fallback 映射 ====================
    // 当 scenic_nfc_marker 表中查不到时使用。
    // scene_code 已修正为 SPOT_0013（不再使用 LS-013）。

    private static final Map<String, NfcCheckinResponse> DEMO_MAPPING = new LinkedHashMap<>();

    static {
        register("NFC_LS_013", 1L, "灵山胜境", 13L, "SPOT_0013", "灵山梵宫",
                "NFC_LS_013", "灵山梵宫NFC标牌",
                "灵山梵宫讲解",
                "灵山梵宫是灵山胜境内建筑规模最大、艺术价值最高的佛教艺术殿堂。");
    }

    private static void register(String markerCode, Long areaId, String areaName,
                                  Long spotId, String sceneCode, String spotName,
                                  String markerCodeVal, String markerName,
                                  String guideTitle, String guideSummary) {
        NfcCheckinResponse resp = new NfcCheckinResponse();
        resp.setAreaId(areaId);
        resp.setAreaName(areaName);
        resp.setTargetType("SPOT");
        resp.setSpotId(spotId);
        resp.setSceneCode(sceneCode);
        resp.setSpotName(spotName);
        resp.setTargetName(spotName);
        resp.setMarkerCode(markerCodeVal);
        resp.setMarkerName(markerName);
        resp.setConfidence(0.98);
        resp.setLocationSource("NFC");
        resp.setTriggerAction("GUIDE");

        NfcCheckinResponse.NfcCheckinGuide guide = new NfcCheckinResponse.NfcCheckinGuide();
        guide.setTitle(guideTitle);
        guide.setSummary(guideSummary);
        resp.setGuide(guide);

        DEMO_MAPPING.put(markerCode, resp);
    }

    // ==================== 主逻辑 ====================

    /**
     * 处理 NFC 打点签到。
     * 优先查 scenic_nfc_marker 表 → fallback 演示映射。
     */
    public NfcCheckinResponse checkin(NfcCheckinRequest request) {
        String markerCode = request.getMarkerCode().trim();
        Long requestAreaId = request.getAreaId();

        log.info("[NfcCheckin] marker_code={}, area_id={}, userId={}, visitId={}, network={}",
                markerCode, requestAreaId, request.getUserId(), request.getVisitId(), request.getNetworkStatus());

        // 1. 优先查 scenic_nfc_marker 表
        NfcMarkerDto marker = null;
        try {
            marker = nfcMarkerMapper.selectByMarkerCode(markerCode);
        } catch (Exception e) {
            log.warn("[NfcCheckin] Failed to query scenic_nfc_marker for {}, will fallback", markerCode, e);
        }

        final NfcCheckinResponse response;

        if (marker != null) {
            // ---------- 数据库命中 ----------
            log.info("[NfcCheckin] DB hit: markerCode={}, targetType={}, targetId={}, targetName={}, sceneCode={}",
                    markerCode, marker.getTargetType(), marker.getTargetId(),
                    marker.getTargetName(), marker.getSceneCode());

            // area_id 校验（只打 warning，不 crash）
            if (requestAreaId != null && marker.getAreaId() != null
                    && !requestAreaId.equals(marker.getAreaId())) {
                log.warn("[NfcCheckin] area_id mismatch: request={}, db={}",
                        requestAreaId, marker.getAreaId());
            }

            response = buildResponseFromMarker(marker, markerCode);

            // 异步更新 hit_count
            try {
                incrementHitCountAsync(marker.getId());
            } catch (Exception e) {
                log.warn("[NfcCheckin] Failed to update hit_count for id={}: {}", marker.getId(), e.getMessage());
            }

        } else {
            // ---------- Fallback: 演示映射 ----------
            log.info("[NfcCheckin] DB miss for {}, trying demo fallback", markerCode);
            response = DEMO_MAPPING.get(markerCode);

            if (response == null) {
                log.warn("[NfcCheckin] marker_code not found in DB or demo: {}", markerCode);
                throw new IllegalArgumentException("未找到 marker_code 对应的点位：" + markerCode);
            }

            // 确保 fallback 也包含 marker_code / marker_name
            if (response.getMarkerCode() == null || response.getMarkerCode().isEmpty()) {
                response.setMarkerCode(markerCode);
            }
            if (response.getMarkerName() == null || response.getMarkerName().isEmpty()) {
                response.setMarkerName(markerCode);
            }
        }

        // 2. 记录 NFC_CHECKIN 行为事件
        recordBehaviorEvent(request, response, marker);

        return response;
    }

    /**
     * 从数据库记录构建响应。
     */
    private NfcCheckinResponse buildResponseFromMarker(NfcMarkerDto m, String markerCode) {
        NfcCheckinResponse resp = new NfcCheckinResponse();
        resp.setAreaId(m.getAreaId());
        resp.setAreaName(m.getAreaName());
        resp.setTargetType(m.getTargetType() != null ? m.getTargetType() : "SPOT");
        resp.setSceneCode(m.getSceneCode());
        resp.setTargetName(m.getTargetName());
        resp.setMarkerCode(nullToEmpty(m.getMarkerCode(), markerCode));
        resp.setMarkerName(nullToEmpty(m.getMarkerName(), markerCode));
        resp.setConfidence(0.98);
        resp.setLocationSource("NFC");
        resp.setTriggerAction("GUIDE");

        if (m.isSpot()) {
            resp.setSpotId(m.getTargetId());
            resp.setSpotName(m.getTargetName());
        }
        if (m.isFacility()) {
            resp.setFacilityId(m.getTargetId());
        }

        // guide
        NfcCheckinResponse.NfcCheckinGuide guide = new NfcCheckinResponse.NfcCheckinGuide();
        String targetName = nullToEmpty(m.getTargetName(), "景点");
        guide.setTitle(nullToEmpty(m.getGuideTitle(), targetName + "讲解"));
        guide.setSummary(nullToEmpty(m.getGuideSummary(),
                targetName + "是" + nullToEmpty(m.getAreaName(), "景区") + "内的重要景点。"));
        resp.setGuide(guide);

        return resp;
    }

    // ==================== 行为事件入库 ====================

    private void recordBehaviorEvent(NfcCheckinRequest request, NfcCheckinResponse response, NfcMarkerDto marker) {
        try {
            BehaviorEventRequest event = new BehaviorEventRequest();
            event.user_id = request.getUserId();
            event.visit_id = request.getVisitId();
            event.area_id = response.getAreaId();

            String targetType = response.getTargetType() != null ? response.getTargetType() : "SPOT";
            event.entity_type = targetType;

            if ("SPOT".equalsIgnoreCase(targetType)) {
                event.spot_id = response.getSpotId();
                event.entity_id = response.getSceneCode();
                event.spot_name = response.getSpotName();
            } else if ("FACILITY".equalsIgnoreCase(targetType)) {
                event.facility_id = response.getFacilityId();
                event.entity_id = response.getSceneCode() != null ? response.getSceneCode()
                        : (response.getFacilityId() != null ? String.valueOf(response.getFacilityId()) : "");
            } else {
                // AREA
                event.entity_id = response.getSceneCode() != null ? response.getSceneCode()
                        : (response.getAreaId() != null ? String.valueOf(response.getAreaId()) : "");
            }

            event.event_type = "NFC_CHECKIN";
            event.event_name = "NFC打点进入" + response.getTargetName();
            event.source = "nfc_android";
            event.location_source = "NFC";
            event.trigger = "nfc_tag_scan";
            event.score = new java.math.BigDecimal("0.98");
            event.park_name = response.getAreaName();
            event.content = buildEventContent(response);

            // scene_code 不直接插入（tourist_behavior_event 表无此列），放入 extra_json
            if (response.getSceneCode() != null && !response.getSceneCode().isEmpty()) {
                event.scene_code = response.getSceneCode();
            }

            // 构建 extra
            java.util.Map<String, Object> extraMap = new LinkedHashMap<>();
            extraMap.put("marker_code", response.getMarkerCode());
            extraMap.put("marker_name", response.getMarkerName());
            extraMap.put("scene_code", response.getSceneCode());
            extraMap.put("location_source", "NFC");
            extraMap.put("network_status", request.getNetworkStatus());
            extraMap.put("target_type", response.getTargetType());
            extraMap.put("target_id",
                    "SPOT".equalsIgnoreCase(response.getTargetType()) ? response.getSpotId()
                    : "FACILITY".equalsIgnoreCase(response.getTargetType()) ? response.getFacilityId()
                    : response.getAreaId());
            event.extra = extraMap;

            String requiredUserId = resolveUserId(request.getUserId());
            behaviorEventService.addBehaviorEvent(event, requiredUserId);
            log.info("[NfcCheckin] NFC_CHECKIN event recorded: {}", response.getMarkerCode());
        } catch (Exception e) {
            log.warn("[NfcCheckin] Failed to record NFC_CHECKIN event (non-fatal): {}", e.getMessage());
        }
    }

    private String buildEventContent(NfcCheckinResponse response) {
        StringBuilder sb = new StringBuilder();
        if (response.getMarkerName() != null && !response.getMarkerName().isEmpty()) {
            sb.append(response.getMarkerName());
        }
        if (response.getGuide() != null && response.getGuide().getSummary() != null
                && !response.getGuide().getSummary().isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(response.getGuide().getSummary());
        }
        return sb.length() > 0 ? sb.toString() : "NFC签到 " + response.getMarkerCode();
    }

    // ==================== 辅助方法 ====================

    private void incrementHitCountAsync(final Long markerId) {
        if (markerId == null) return;
        try {
            new Thread(() -> {
                try {
                    int rows = nfcMarkerMapper.incrementHitCount(markerId);
                    log.debug("[NfcCheckin] hit_count updated for id={}, rows={}", markerId, rows);
                } catch (Exception e) {
                    log.warn("[NfcCheckin] hit_count update failed for id={}: {}", markerId, e.getMessage());
                }
            }, "nfc-hit-count").start();
        } catch (Exception e) {
            log.warn("[NfcCheckin] Failed to start hit_count thread for id={}", markerId, e);
        }
    }

    private String resolveUserId(String userId) {
        if (userId != null && !userId.trim().isEmpty()
                && !"anonymous".equalsIgnoreCase(userId.trim())
                && !userId.trim().startsWith("visitor_")
                && !userId.trim().startsWith("android-live2d-")) {
            return userId.trim();
        }
        return "";
    }

    private String nullToEmpty(String value, String fallback) {
        if (value != null && !value.trim().isEmpty()) return value.trim();
        return fallback != null ? fallback : "";
    }
}
