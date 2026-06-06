package com.scenic.ai.modules.app.visit.controller;

import com.scenic.ai.modules.app.user.dto.ApiResult;
import com.scenic.ai.modules.app.user.dto.UserInfoDto;
import com.scenic.ai.modules.app.user.service.AppUserService;
import com.scenic.ai.modules.app.visit.dto.SpotEnterRequest;
import com.scenic.ai.modules.app.visit.dto.SpotEnterResponse;
import com.scenic.ai.modules.app.visit.dto.SpotLeaveRequest;
import com.scenic.ai.modules.app.visit.dto.SpotLeaveResponse;
import com.scenic.ai.modules.app.visit.dto.VisitEndRequest;
import com.scenic.ai.modules.app.visit.dto.VisitEndResponse;
import com.scenic.ai.modules.app.visit.dto.VisitReportDetailResponse;
import com.scenic.ai.modules.app.visit.dto.VisitStartRequest;
import com.scenic.ai.modules.app.visit.dto.VisitStartResponse;
import com.scenic.ai.modules.app.visit.dto.VisitStatusResponse;
import com.scenic.ai.modules.app.visit.service.VisitReportService;
import com.scenic.ai.modules.app.visit.service.VisitService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/app/visit")
@CrossOrigin
public class AppVisitController {

    private final VisitService visitService;
    private final VisitReportService visitReportService;
    private final AppUserService appUserService;

    public AppVisitController(
            VisitService visitService,
            VisitReportService visitReportService,
            AppUserService appUserService
    ) {
        this.visitService = visitService;
        this.visitReportService = visitReportService;
        this.appUserService = appUserService;
    }

    @PostMapping("/start")
    public ApiResult<VisitStartResponse> startVisit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) VisitStartRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("请求参数不能为空");
            }
            String userId = appUserService.resolveRequiredUserId(authorization, request.getUserIdText());
            request.userId = userId;
            request.user_id = userId;
            return ok(visitService.startVisit(request));
        } catch (IllegalArgumentException e) {
            return fail(e);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("创建现场导览失败");
        }
    }

    @GetMapping("/status")
    public ApiResult<VisitStatusResponse> status(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "visitId", required = false) String visitId,
            @RequestParam(value = "visit_id", required = false) String visitIdSnake,
            @RequestParam(value = "areaId", required = false) String areaId,
            @RequestParam(value = "area_id", required = false) String areaIdSnake,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "user_id", required = false) String userIdSnake
    ) {
        try {
            String resolvedUserId = appUserService.resolveRequiredUserId(
                    authorization,
                    firstNotBlank(userId, userIdSnake)
            );
            VisitStatusResponse response;
            String visitIdText = firstNotBlank(visitId, visitIdSnake);
            if (visitIdText.isEmpty()) {
                response = visitService.getVisitOverview(resolvedUserId, firstNotBlank(areaId, areaIdSnake));
            } else {
                response = visitService.getVisitStatus(parseRequiredLong(visitIdText, "visitId 不能为空"), resolvedUserId);
                VisitStatusResponse overview = visitService.getVisitOverview(resolvedUserId, firstNotBlank(areaId, areaIdSnake));
                response.hasLastReport = overview.hasLastReport;
                response.lastReportVisitId = overview.lastReportVisitId;
                response.lastReportAreaName = overview.lastReportAreaName;
                response.lastFinishedAt = overview.lastFinishedAt;
            }
            attachUserInfo(response, resolvedUserId);
            return ok(response);
        } catch (IllegalArgumentException e) {
            return fail(e);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("查询游览状态失败");
        }
    }

    @GetMapping("/current")
    public ApiResult<VisitStatusResponse> current(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "areaId", required = false) String areaId,
            @RequestParam(value = "area_id", required = false) String areaIdSnake,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "user_id", required = false) String userIdSnake
    ) {
        try {
            String resolvedUserId = appUserService.resolveRequiredUserId(
                    authorization,
                    firstNotBlank(userId, userIdSnake)
            );
            VisitStatusResponse response = visitService.getVisitOverview(resolvedUserId, firstNotBlank(areaId, areaIdSnake));
            attachUserInfo(response, resolvedUserId);
            return ok(response);
        } catch (IllegalArgumentException e) {
            return fail(e);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("查询当前游览失败");
        }
    }

    @GetMapping("/report/detail")
    public ApiResult<VisitReportDetailResponse> reportDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "visitId", required = false) String visitId,
            @RequestParam(value = "visit_id", required = false) String visitIdSnake,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "user_id", required = false) String userIdSnake
    ) {
        try {
            String resolvedUserId = appUserService.resolveRequiredUserId(
                    authorization,
                    firstNotBlank(userId, userIdSnake)
            );
            return ok(visitReportService.getVisitReportDetail(
                    parseRequiredLong(firstNotBlank(visitId, visitIdSnake), "visitId 不能为空"),
                    resolvedUserId
            ));
        } catch (IllegalArgumentException e) {
            return fail(e);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("获取游玩报告详情失败");
        }
    }

    @PostMapping("/spot/enter")
    public ApiResult<SpotEnterResponse> enterSpot(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) SpotEnterRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("请求参数不能为空");
            }
            String userId = appUserService.resolveRequiredUserId(authorization, request.getUserIdText());
            request.userId = userId;
            return ok(visitService.enterScenic(request));
        } catch (IllegalArgumentException e) {
            return fail(e);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("景点到达入库失败");
        }
    }

    @PostMapping("/spot/leave")
    public ApiResult<SpotLeaveResponse> leaveSpot(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) SpotLeaveRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("请求参数不能为空");
            }
            String userId = appUserService.resolveRequiredUserId(authorization, request.getUserIdText());
            request.userId = userId;
            return ok(visitService.leaveScenic(request));
        } catch (IllegalArgumentException e) {
            return fail(e);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("景点离开入库失败");
        }
    }

    @PostMapping("/end")
    public ApiResult<VisitEndResponse> endVisit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) VisitEndRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("请求参数不能为空");
            }
            String userId = appUserService.resolveRequiredUserId(authorization, request.getUserIdText());
            request.userId = userId;
            return ok(visitService.endVisit(request));
        } catch (IllegalArgumentException e) {
            return fail(e);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("结束游览入库失败");
        }
    }

    private <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "success", data);
    }

    private <T> ApiResult<T> fail(IllegalArgumentException e) {
        if (isAuthError(e.getMessage())) {
            return ApiResult.fail(401, "请先登录");
        }
        return ApiResult.fail(400, e.getMessage());
    }

    private boolean isAuthError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("请先登录")
                || message.contains("登录已过期")
                || message.contains("当前为临时用户");
    }

    private void attachUserInfo(VisitStatusResponse response, String userId) {
        if (response == null) {
            return;
        }

        UserInfoDto userInfo = appUserService.getUserInfoByUserId(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("user_id", userId);
        if (userInfo != null) {
            data.put("nickname", userInfo.nickname);
            data.put("avatarUrl", userInfo.avatar_url);
            data.put("avatar_url", userInfo.avatar_url);
            data.put("phone", userInfo.phone);
        }
        response.userInfo = data;
    }

    private Long parseRequiredLong(String value, String emptyMessage) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(emptyMessage);
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
