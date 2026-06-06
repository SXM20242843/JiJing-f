package com.scenic.ai.modules.app.user.controller;

import com.scenic.ai.modules.app.user.dto.ApiResult;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.service.AppUserService;
import com.scenic.ai.modules.app.user.service.BehaviorEventService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/event")
@CrossOrigin
public class EventTrackController {

    private final AppUserService appUserService;
    private final BehaviorEventService behaviorEventService;

    public EventTrackController(
            AppUserService appUserService,
            BehaviorEventService behaviorEventService
    ) {
        this.appUserService = appUserService;
        this.behaviorEventService = behaviorEventService;
    }

    @PostMapping("/track")
    public ApiResult<Map<String, Object>> track(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) BehaviorEventRequest request
    ) {
        try {
            BehaviorEventRequest event = request == null ? new BehaviorEventRequest() : request;
            if (event.getEventTypeText().isEmpty()) {
                event.eventType = "unknown_event";
            }
            String userId = appUserService.resolveRequiredUserId(authorization, event.getUserIdText());
            String eventId = behaviorEventService.addBehaviorEvent(event, userId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("eventId", eventId);
            data.put("event_id", eventId);
            return new ApiResult<>(0, "success", data);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("行为事件入库失败");
        }
    }
}
