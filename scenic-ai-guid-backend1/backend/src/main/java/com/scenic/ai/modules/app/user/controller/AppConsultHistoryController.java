package com.scenic.ai.modules.app.user.controller;

import com.scenic.ai.modules.app.user.dto.ApiResult;
import com.scenic.ai.modules.app.user.dto.ConsultHistoryItemDto;
import com.scenic.ai.modules.app.user.service.ConsultHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({
        "/api/app/user/consult",
        "/api/user/consult"
})
@CrossOrigin
public class AppConsultHistoryController {

    private final ConsultHistoryService consultHistoryService;

    public AppConsultHistoryController(ConsultHistoryService consultHistoryService) {
        this.consultHistoryService = consultHistoryService;
    }

    @GetMapping("/history")
    public ApiResult<List<ConsultHistoryItemDto>> history(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "user_id", required = false) String userIdSnake,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        try {
            String providedUserId = firstNotBlank(userId, userIdSnake);
            Integer finalLimit = pageSize != null ? pageSize : limit;

            return ApiResult.ok(
                    consultHistoryService.listHistory(authorization, providedUserId, finalLimit)
            );
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("获取咨询记录失败");
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