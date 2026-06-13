package com.scenic.ai.modules.app.location.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.location.dto.BehaviorBatchSyncRequest;
import com.scenic.ai.modules.app.location.dto.BehaviorBatchSyncResponse;
import com.scenic.ai.modules.app.location.service.BehaviorBatchSyncService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/behavior")
@CrossOrigin
public class BehaviorBatchSyncController {

    private final BehaviorBatchSyncService behaviorBatchSyncService;

    public BehaviorBatchSyncController(BehaviorBatchSyncService behaviorBatchSyncService) {
        this.behaviorBatchSyncService = behaviorBatchSyncService;
    }

    @PostMapping("/batch-sync")
    public ApiResponse<BehaviorBatchSyncResponse> batchSync(@RequestBody BehaviorBatchSyncRequest request) {
        try {
            if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
                return new ApiResponse<>(400, "user_id不能为空", null);
            }
            if (request.getEvents() == null || request.getEvents().isEmpty()) {
                return new ApiResponse<>(400, "events不能为空", null);
            }

            BehaviorBatchSyncResponse response = behaviorBatchSyncService.batchSync(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponse<>(500, "批量同步失败", null);
        }
    }
}
