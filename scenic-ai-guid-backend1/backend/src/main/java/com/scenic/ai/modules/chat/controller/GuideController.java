package com.scenic.ai.modules.chat.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.user.service.AppUserService;
import com.scenic.ai.modules.chat.dto.GuideChatRequest;
import com.scenic.ai.modules.chat.dto.GuideChatResponse;
import com.scenic.ai.modules.chat.service.GuideChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/guide")
public class GuideController {

    private final GuideChatService guideChatService;
    private final AppUserService appUserService;

    public GuideController(GuideChatService guideChatService, AppUserService appUserService) {
        this.guideChatService = guideChatService;
        this.appUserService = appUserService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE, headers = {"Accept", "Accept!=*/*"})
    public ResponseEntity<StreamingResponseBody> chatStream(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody GuideChatRequest request
    ) {
        try {
            String userId = resolveGuideChatUserId(authorization, request);
            request.setUserId(userId);
            StreamingResponseBody body = guideChatService.streamAiService(request);
            return ResponseEntity.ok()
                    .headers(sseHeaders())
                    .body(body);
        } catch (IllegalArgumentException e) {
            if (isAuthError(e.getMessage())) {
                return sseErrorResponse(401, "请先登录");
            }
            return sseErrorResponse(400, e.getMessage());
        }
    }

    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<GuideChatResponse>> chatJson(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody GuideChatRequest request
    ) {
        try {
            String userId = resolveGuideChatUserId(authorization, request);
            request.setUserId(userId);
            GuideChatResponse response = guideChatService.callAiService(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            if (isAuthError(e.getMessage())) {
                return ResponseEntity.ok(new ApiResponse<>(401, "请先登录", null));
            }
            return ResponseEntity.ok(new ApiResponse<>(400, e.getMessage(), null));
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

    private String resolveGuideChatUserId(String authorization, GuideChatRequest request) {
        String providedUserId = firstNotBlank(request.getUserId(), request.getLoginUserId(), request.getVisitorId());
        try {
            return appUserService.resolveRequiredUserId(authorization, providedUserId);
        } catch (IllegalArgumentException e) {
            if (isAnonymousLike(providedUserId)) {
                return firstNotBlank(providedUserId, "anonymous");
            }
            throw e;
        }
    }

    private boolean isAnonymousLike(String userId) {
        String value = userId == null ? "" : userId.trim().toLowerCase();
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

    private HttpHeaders sseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/event-stream;charset=UTF-8");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
        headers.set("X-Accel-Buffering", "no");
        return headers;
    }

    private ResponseEntity<StreamingResponseBody> sseErrorResponse(int code, String message) {
        StreamingResponseBody body = outputStream -> {
            String payload = "event: error\n"
                    + "data: {\"code\":" + code + ",\"message\":\"" + escapeJson(message) + "\"}\n\n";
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };
        return ResponseEntity.ok()
                .headers(sseHeaders())
                .body(body);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
    }
}
