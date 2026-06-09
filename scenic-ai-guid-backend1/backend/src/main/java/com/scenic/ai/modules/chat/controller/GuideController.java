package com.scenic.ai.modules.chat.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.user.service.AppUserService;
import com.scenic.ai.modules.chat.dto.GuideChatRequest;
import com.scenic.ai.modules.chat.dto.GuideChatResponse;
import com.scenic.ai.modules.chat.service.GuideChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guide")
public class GuideController {

    private final GuideChatService guideChatService;
    private final AppUserService appUserService;

    public GuideController(GuideChatService guideChatService, AppUserService appUserService) {
        this.guideChatService = guideChatService;
        this.appUserService = appUserService;
    }

    @PostMapping("/chat")
    public ApiResponse<GuideChatResponse> chat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody GuideChatRequest request
    ) {
        try {
            String userId = appUserService.resolveRequiredUserId(authorization, request.getEffectiveUserId());
            request.setUserId(userId);
            GuideChatResponse response = guideChatService.callAiService(request);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            if (isAuthError(e.getMessage())) {
                return new ApiResponse<>(401, "请先登录", null);
            }
            return new ApiResponse<>(400, e.getMessage(), null);
        }
    }

    @GetMapping("/tts/status")
    public ApiResponse<GuideChatResponse> ttsStatus(
            @RequestParam("taskId") String taskId
    ) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return new ApiResponse<>(400, "taskId不能为空", null);
        }
        GuideChatResponse response = guideChatService.queryTtsStatus(taskId.trim());
        return ApiResponse.success(response);
    }

    private boolean isAuthError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("请先登录")
                || message.contains("登录已过期")
                || message.contains("当前为临时用户");
    }
}
