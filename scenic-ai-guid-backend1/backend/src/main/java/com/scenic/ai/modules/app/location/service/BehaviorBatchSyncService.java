package com.scenic.ai.modules.app.location.service;

import com.scenic.ai.modules.app.location.dto.BehaviorBatchSyncRequest;
import com.scenic.ai.modules.app.location.dto.BehaviorBatchSyncResponse;
import com.scenic.ai.modules.app.location.dto.BehaviorEventItem;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.service.BehaviorEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 批量同步离线行为事件服务。
 * 复用现有 BehaviorEventService 和 tourist_behavior_event 表。
 */
@Slf4j
@Service
public class BehaviorBatchSyncService {

    private final BehaviorEventService behaviorEventService;

    public BehaviorBatchSyncService(BehaviorEventService behaviorEventService) {
        this.behaviorEventService = behaviorEventService;
    }

    public BehaviorBatchSyncResponse batchSync(BehaviorBatchSyncRequest request) {
        BehaviorBatchSyncResponse response = new BehaviorBatchSyncResponse();
        int received = 0;
        int success = 0;
        int failed = 0;

        if (request.getEvents() == null || request.getEvents().isEmpty()) {
            response.setReceived(0);
            response.setSuccess(0);
            response.setFailed(0);
            return response;
        }

        received = request.getEvents().size();

        for (BehaviorEventItem item : request.getEvents()) {
            try {
                // 构建 BehaviorEventRequest 复用现有入库逻辑
                BehaviorEventRequest event = new BehaviorEventRequest();
                event.event_id = item.getEventId();
                event.user_id = resolveUserId(request.getUserId(), item.getUserId());
                event.visit_id = item.getVisitId();
                event.area_id = item.getAreaId();
                event.spot_id = item.getSpotId();
                event.scene_code = item.getSceneCode();
                event.event_type = item.getEventType() != null ? item.getEventType() : "NFC_CHECKIN";
                event.event_name = item.getEventName();
                event.source = "nfc_offline_sync";
                event.location_source = "NFC";
                event.trigger = "offline_queue_sync";

                // NFC_CHECKIN 事件：entity_type=SPOT, entity_id=scene_code
                String eventType = item.getEventType() != null ? item.getEventType() : "NFC_CHECKIN";
                if ("NFC_CHECKIN".equalsIgnoreCase(eventType)) {
                    event.entity_type = "SPOT";
                    if (item.getSceneCode() != null && !item.getSceneCode().isEmpty()) {
                        event.entity_id = item.getSceneCode();
                    }
                }

                // extra_json：保存 scene_code、marker_code 等（不插入 scene_code 列）
                java.util.Map<String, Object> batchExtra = new java.util.LinkedHashMap<>();
                if (item.getSceneCode() != null && !item.getSceneCode().isEmpty()) {
                    batchExtra.put("scene_code", item.getSceneCode());
                }
                if (item.getMarkerCode() != null && !item.getMarkerCode().isEmpty()) {
                    batchExtra.put("marker_code", item.getMarkerCode());
                }
                batchExtra.put("synced_from", "offline_queue");
                if (!batchExtra.isEmpty()) {
                    event.extra = batchExtra;
                }

                // event_id 作为幂等键（INSERT IGNORE 防重）
                String requiredUserId = resolveUserId(request.getUserId(), item.getUserId());
                if (requiredUserId.isEmpty()) {
                    log.warn("[BatchSync] Skipping event {} — no valid userId", item.getEventId());
                    failed++;
                    continue;
                }

                try {
                    behaviorEventService.addBehaviorEvent(event, requiredUserId);
                    success++;
                } catch (DataIntegrityViolationException e) {
                    // INSERT IGNORE 兜底：event_id 重复时 DB 约束仍可能抛异常，视为幂等成功
                    log.info("[BatchSync] Duplicate event_id={} (DB constraint), treated as success", item.getEventId());
                    success++;
                } catch (IllegalArgumentException e) {
                    // 业务校验失败（如缺少 userId），仍计入 failed
                    log.warn("[BatchSync] Event {} failed validation: {}", item.getEventId(), e.getMessage());
                    failed++;
                }
            } catch (Exception e) {
                log.warn("[BatchSync] Event {} failed with exception", item.getEventId(), e);
                failed++;
            }
        }

        response.setReceived(received);
        response.setSuccess(success);
        response.setFailed(failed);

        log.info("[BatchSync] result: received={}, success={}, failed={}", received, success, failed);
        return response;
    }

    private String resolveUserId(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()
                    && !"anonymous".equalsIgnoreCase(value.trim())
                    && !value.trim().startsWith("visitor_")
                    && !value.trim().startsWith("android-live2d-")) {
                return value.trim();
            }
        }
        return "";
    }
}
