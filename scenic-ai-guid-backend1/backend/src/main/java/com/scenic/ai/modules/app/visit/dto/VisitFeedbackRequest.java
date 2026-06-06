package com.scenic.ai.modules.app.visit.dto;

public class VisitFeedbackRequest {

    public Long visit_id;
    public Long visitId;

    public Integer satisfaction;
    public String comment;
    public Boolean recommend;

    public Long getVisitIdValue() {
        return visit_id != null ? visit_id : visitId;
    }

    public String getCommentText() {
        return comment == null ? null : comment.trim();
    }
}
