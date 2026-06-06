package com.scenic.ai.modules.app.visit.service;

import com.scenic.ai.modules.app.visit.dto.VisitFeedbackRequest;
import com.scenic.ai.modules.app.visit.dto.VisitFeedbackResponse;
import com.scenic.ai.modules.app.visit.entity.TouristVisitSession;
import com.scenic.ai.modules.app.visit.mapper.VisitFeedbackMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class VisitFeedbackService {

    private final VisitFeedbackMapper visitFeedbackMapper;

    public VisitFeedbackService(VisitFeedbackMapper visitFeedbackMapper) {
        this.visitFeedbackMapper = visitFeedbackMapper;
    }

    @Transactional
    public VisitFeedbackResponse submitFeedback(VisitFeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        Long visitId = request.getVisitIdValue();
        Integer satisfaction = request.satisfaction;

        if (visitId == null) {
            throw new IllegalArgumentException("visitId 不能为空");
        }

        if (satisfaction == null || satisfaction < 1 || satisfaction > 5) {
            throw new IllegalArgumentException("satisfaction 必须在 1-5 之间");
        }

        TouristVisitSession session = visitFeedbackMapper.selectVisitSessionById(visitId);
        if (session == null) {
            throw new IllegalArgumentException("游玩记录不存在");
        }

        String comment = request.getCommentText();
        String sessionComment = limitLength(comment, 500);
        BigDecimal score = BigDecimal.valueOf(satisfaction);
        LocalDateTime now = LocalDateTime.now();

        Long recordId = visitFeedbackMapper.selectLatestSatisfactionRecordId(visitId);
        int rows;
        if (recordId == null) {
            rows = visitFeedbackMapper.insertSatisfactionRecord(
                    session.userId,
                    session.chatSessionId,
                    visitId,
                    session.areaId,
                    score,
                    comment,
                    now
            );
        } else {
            rows = visitFeedbackMapper.updateSatisfactionRecord(recordId, score, comment);
        }

        if (rows <= 0) {
            throw new IllegalStateException("保存满意度反馈失败");
        }

        int sessionRows = visitFeedbackMapper.updateVisitSessionFeedback(
                visitId,
                satisfaction,
                sessionComment,
                toRecommendValue(request.recommend),
                now
        );
        if (sessionRows <= 0) {
            throw new IllegalStateException("回写游玩反馈失败");
        }

        return new VisitFeedbackResponse(
                visitId,
                satisfaction,
                sessionComment,
                request.recommend
        );
    }

    private Integer toRecommendValue(Boolean recommend) {
        if (recommend == null) {
            return null;
        }

        return recommend ? 1 : 0;
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
