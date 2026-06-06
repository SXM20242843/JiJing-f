package com.scenic.ai.modules.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.modules.app.user.mapper.AiQuestionBehaviorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AiQuestionBehaviorService {

    private final AiQuestionBehaviorMapper aiQuestionBehaviorMapper;
    private final ObjectMapper objectMapper;

    public AiQuestionBehaviorService(
            AiQuestionBehaviorMapper aiQuestionBehaviorMapper,
            ObjectMapper objectMapper
    ) {
        this.aiQuestionBehaviorMapper = aiQuestionBehaviorMapper;
        this.objectMapper = objectMapper;
    }

    public void recordAiQuestionSafely(
            String visitId,
            String userId,
            String sessionId,
            String parkId,
            String parkName,
            String scenicId,
            String scenicName,
            String question,
            String inputType
    ) {
        try {
            Long visitIdValue = parseVisitId(visitId);

            String finalUserId = firstNotBlank(userId);
            if (isTemporaryUserId(finalUserId)) {
                return;
            }

            String finalParkId = firstNotBlank(parkId);
            String finalScenicId = firstNotBlank(scenicId);

            Long areaId = finalParkId.isEmpty()
                    ? null
                    : aiQuestionBehaviorMapper.selectAreaIdByAreaCode(finalParkId);
            Long spotId = finalScenicId.isEmpty()
                    ? null
                    : aiQuestionBehaviorMapper.selectSpotIdBySceneCode(finalScenicId);

            String extraJson = buildExtraJson(
                    visitIdValue,
                    finalParkId,
                    parkName,
                    finalScenicId,
                    scenicName,
                    inputType
            );

            aiQuestionBehaviorMapper.insertAiQuestionEvent(
                    generateEventId(),
                    finalUserId,
                    firstNotBlank(sessionId),
                    visitIdValue,
                    areaId,
                    spotId,
                    spotId == null ? "CHAT" : "SPOT",
                    finalScenicId,
                    "voice".equalsIgnoreCase(firstNotBlank(inputType)) ? "VOICE_INPUT" : "ASK",
                    firstNotBlank(question),
                    null,
                    null,
                    extraJson
            );
        } catch (Exception e) {
            log.warn("记录AI提问行为失败，不影响AI回答。visitId={}, userId={}, scenicId={}",
                    visitId,
                    userId,
                    scenicId,
                    e);
        }
    }

    private boolean isTemporaryUserId(String userId) {
        if (userId == null) {
            return true;
        }

        String value = userId.trim().toLowerCase();
        return value.isEmpty()
                || "anonymous".equals(value)
                || value.startsWith("visitor_")
                || value.startsWith("android-live2d-");
    }

    private String buildExtraJson(
            Long visitId,
            String parkId,
            String parkName,
            String scenicId,
            String scenicName,
            String inputType
    ) {
        try {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("visitId", visitId);
            extra.put("parkId", firstNotBlank(parkId));
            extra.put("parkName", firstNotBlank(parkName));
            extra.put("scenicId", firstNotBlank(scenicId));
            extra.put("scenicName", firstNotBlank(scenicName));
            extra.put("inputType", firstNotBlank(inputType, "auto"));
            extra.put("source", "native-live2d-guide");
            return objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Long parseVisitId(String visitId) {
        String value = firstNotBlank(visitId);
        if (value.isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("AI提问行为 visitId 非数字，跳过本次统计。visitId={}", visitId);
            return null;
        }
    }

    private String generateEventId() {
        return "aiq_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
