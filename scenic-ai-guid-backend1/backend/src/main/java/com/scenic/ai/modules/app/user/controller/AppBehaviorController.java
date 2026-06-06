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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/app/behavior")
@CrossOrigin
public class AppBehaviorController {

    private final AppUserService appUserService;
    private final BehaviorEventService behaviorEventService;

    public AppBehaviorController(
            AppUserService appUserService,
            BehaviorEventService behaviorEventService
    ) {
        this.appUserService = appUserService;
        this.behaviorEventService = behaviorEventService;
    }

    @PostMapping("/event")
    public ApiResult<Map<String, Object>> addBehaviorEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) BehaviorEventRequest request
    ) {
        try {
            String providedUserId = request == null ? null : request.getUserIdText();
            String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);
            String eventId = behaviorEventService.addBehaviorEvent(request, userId);

            Map<String, Object> data = new HashMap<>();
            data.put("event_id", eventId);
            return ApiResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("行为事件入库失败");
        }
    }
}
