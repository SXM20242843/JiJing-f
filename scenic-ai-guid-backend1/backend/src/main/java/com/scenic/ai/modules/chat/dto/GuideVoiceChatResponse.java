package com.scenic.ai.modules.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scenic.ai.modules.app.route.dto.RouteCardDto;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GuideVoiceChatResponse {
    private String taskId;
    private String questionText;
    private String recognizedText;
    private String conversationId;
    private String messageId;
    private String answer;
    private String rewrittenQuestion;
    private String intent;
    private Map<String, Object> audio;
    private Map<String, Object> mouth;
    private Map<String, Object> digitalHuman;
    private String audioUrl;
    private String audioFormat;
    private String audioStatus;
    private String ttsTaskId;
    private Long audioDurationMs;
    private String ttsError;
    private Map<String, Object> currentEntity;
    private List<MouthFrameDto> mouthFrames;
    private String mouthStatus;
    private String emotion;
    private String emotionCode;
    private String action;
    private String actionCode;
    private String avatarId;
    private RouteCardDto route;
    private Object routeRecommendation;

    @JsonProperty("route_recommendation")
    private Object routeRecommendationSnake;
    private List<String> suggestions;
    private List<Map<String, Object>> sources;

    // 新增字段
    private String visitId;
}
