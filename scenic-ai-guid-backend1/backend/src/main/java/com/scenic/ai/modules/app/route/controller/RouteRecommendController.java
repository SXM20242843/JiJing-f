package com.scenic.ai.modules.app.route.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.route.dto.RouteRecommendRequest;
import com.scenic.ai.modules.app.route.dto.RouteRecommendResponse;
import com.scenic.ai.modules.app.route.service.RouteRecommendService;
import com.scenic.ai.modules.app.user.service.AppUserService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guide/route")
@CrossOrigin
public class RouteRecommendController {

    private final RouteRecommendService routeRecommendService;
    private final AppUserService appUserService;

    public RouteRecommendController(
            RouteRecommendService routeRecommendService,
            AppUserService appUserService
    ) {
        this.routeRecommendService = routeRecommendService;
        this.appUserService = appUserService;
    }

    @PostMapping("/recommend")
    public ApiResponse<RouteRecommendResponse> recommendRoute(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) RouteRecommendRequest request
    ) {
        try {
            String providedUserId = request == null ? null : request.getUserIdText();
            String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);
            RouteRecommendRequest routeRequest = request == null ? new RouteRecommendRequest() : request;
            return ApiResponse.success(routeRecommendService.recommendRoute(routeRequest, userId));
        } catch (IllegalArgumentException e) {
            if (isAuthError(e.getMessage())) {
                return new ApiResponse<>(401, "请先登录", null);
            }
            return new ApiResponse<>(400, e.getMessage(), null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.fail("路线推荐失败，请稍后重试");
        }
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
