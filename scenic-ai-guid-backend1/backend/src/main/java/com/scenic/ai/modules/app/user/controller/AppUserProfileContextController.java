package com.scenic.ai.modules.app.user.controller;

import com.scenic.ai.modules.app.route.dto.profile.UserProfileAiContextResponse;
import com.scenic.ai.modules.app.user.dto.ApiResult;
import com.scenic.ai.modules.app.user.service.UserProfileService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/user/profile")
@CrossOrigin
public class AppUserProfileContextController {

    private final UserProfileService userProfileService;

    public AppUserProfileContextController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/ai-context")
    public ApiResult<UserProfileAiContextResponse> aiContext(
            @RequestParam("userId") String userId,
            @RequestParam(value = "areaId", required = false) String areaId,
            @RequestParam(value = "area_id", required = false) String areaIdSnake,
            @RequestParam(value = "visitId", required = false) String visitId,
            @RequestParam(value = "visit_id", required = false) String visitIdSnake,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "currentSpotId", required = false) String currentSpotId,
            @RequestParam(value = "currentSpotName", required = false) String currentSpotName
    ) {
        return ApiResult.ok(userProfileService.buildAiProfileContext(
                userId,
                parseLong(firstNotBlank(areaId, areaIdSnake)),
                parseLong(firstNotBlank(visitId, visitIdSnake)),
                sessionId,
                currentSpotId,
                currentSpotName
        ));
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
