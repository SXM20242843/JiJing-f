package com.scenic.ai.modules.app.visit.dto;

public class VisitFeedbackResponse {

    public Long visitId;
    public Integer satisfaction;
    public String comment;
    public Boolean recommend;

    public VisitFeedbackResponse() {
    }

    public VisitFeedbackResponse(
            Long visitId,
            Integer satisfaction,
            String comment,
            Boolean recommend
    ) {
        this.visitId = visitId;
        this.satisfaction = satisfaction;
        this.comment = comment;
        this.recommend = recommend;
    }
}
