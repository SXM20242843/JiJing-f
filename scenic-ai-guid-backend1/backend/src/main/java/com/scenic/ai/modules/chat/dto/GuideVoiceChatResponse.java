package com.scenic.ai.modules.chat.dto;

import com.scenic.ai.modules.app.route.dto.RouteCardDto;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GuideVoiceChatResponse {
    private String questionText;
    private String recognizedText;
    private String conversationId;
    private String messageId;
    private String answer;
    private String rewrittenQuestion;
    private String intent;
    private String audioUrl;
    private String audioFormat;
    private String ttsError;
    private Map<String, Object> currentEntity;
    private List<MouthFrameDto> mouthFrames;
    private RouteCardDto route;
    private List<String> suggestions;
    private List<Map<String, Object>> sources;

    // 新增字段
    private String visitId;
}
