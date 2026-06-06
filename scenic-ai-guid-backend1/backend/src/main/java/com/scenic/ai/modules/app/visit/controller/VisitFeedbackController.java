package com.scenic.ai.modules.app.visit.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.visit.dto.VisitFeedbackRequest;
import com.scenic.ai.modules.app.visit.dto.VisitFeedbackResponse;
import com.scenic.ai.modules.app.visit.service.VisitFeedbackService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visit")
@CrossOrigin
public class VisitFeedbackController {

    private final VisitFeedbackService visitFeedbackService;

    public VisitFeedbackController(VisitFeedbackService visitFeedbackService) {
        this.visitFeedbackService = visitFeedbackService;
    }

    @PostMapping("/feedback")
    public ApiResponse<VisitFeedbackResponse> submitFeedback(
            @RequestBody(required = false) VisitFeedbackRequest request
    ) {
        try {
            return ApiResponse.success(visitFeedbackService.submitFeedback(request));
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(400, e.getMessage(), null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.fail("提交游玩反馈失败，请稍后重试");
        }
    }
}
