package com.scenic.ai.modules.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.common.config.AiProperties;
import com.scenic.ai.modules.app.route.service.RouteRecommendService;
import com.scenic.ai.modules.app.visit.dto.VisitStartResponse;
import com.scenic.ai.modules.app.visit.service.VisitService;
import com.scenic.ai.modules.chat.dto.GuideChatRequest;
import com.scenic.ai.modules.chat.dto.GuideChatResponse;
import com.scenic.ai.modules.chat.dto.MouthFrameDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GuideChatService {

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiQuestionBehaviorService aiQuestionBehaviorService;
    private final VisitService visitService;  // 注入
    private final ChatPersistenceService chatPersistenceService;
    private final RouteRecommendService routeRecommendService;

    public GuideChatService(RestTemplate restTemplate,
                            AiProperties aiProperties,
                            ObjectMapper objectMapper,
                            AiQuestionBehaviorService aiQuestionBehaviorService,
                            VisitService visitService,
                            ChatPersistenceService chatPersistenceService,
                            RouteRecommendService routeRecommendService) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.aiQuestionBehaviorService = aiQuestionBehaviorService;
        this.visitService = visitService;
        this.chatPersistenceService = chatPersistenceService;
        this.routeRecommendService = routeRecommendService;
    }

    public GuideChatResponse callAiService(GuideChatRequest request) {
        chatPersistenceService.ensureTextSessionId(request);

        // ========== 1. 确保游览会话存在 ==========
        String visitId = ensureVisitSession(request);
        if (visitId != null && !visitId.isEmpty()) {
            request.setVisitId(visitId);
            visitService.bindChatSessionId(
                    parseLongOrNull(visitId),
                    request.getEffectiveUserId(),
                    request.getEffectiveSessionId()
            );
        }
        // =====================================

        try {
            Map<String, Object> requestBody = buildAiRequestBody(request);
            RouteRecommendService.ChatRouteContext routeContext = null;
            try {
                routeContext = routeRecommendService.enrichChatRouteRequest(
                        request,
                        request.getEffectiveUserId(),
                        requestBody
                );
            } catch (Exception e) {
                log.warn("Chat 路线请求增强失败，继续普通问答。userId={}, sessionId={}",
                        request.getEffectiveUserId(), request.getEffectiveSessionId(), e);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = aiProperties.getBaseUrl() + aiProperties.getTextChatEndpoint();
            log.info("调用AI文本问答服务: {}", url);
            Object candidateSpots = requestBody.get("candidate_spots");
            int candidateSpotCount = candidateSpots instanceof List<?> list ? list.size() : 0;
            log.info("AI文本问答请求字段: userId={}, sessionId={}, route={}, candidate_spots={}, requestKeys={}",
                    request.getEffectiveUserId(),
                    request.getEffectiveSessionId(),
                    requestBody.get("route"),
                    candidateSpotCount,
                    requestBody.keySet());

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("AI文本问答服务响应成功: status={}, responseKeys={}",
                        response.getStatusCode(), responseBody.keySet());
                GuideChatResponse result = parseAiResponse(responseBody);
                // 将 visitId 设置到响应中，返回给 Android 端
                result.setVisitId(visitId);
                if (containsRoutePayload(responseBody) && !Boolean.TRUE.equals(request.getRoute())) {
                    request.setRoute(true);
                    routeContext = routeRecommendService.enrichChatRouteRequest(
                            request,
                            request.getEffectiveUserId(),
                            new LinkedHashMap<>(requestBody)
                    );
                }
                try {
                    result.setRoute(routeRecommendService.standardizeAndSaveChatRoute(
                            request,
                            request.getEffectiveUserId(),
                            request.getEffectiveSessionId(),
                            responseBody,
                            result.getAnswer(),
                            routeContext
                    ));
                } catch (Exception e) {
                    log.warn("Chat 路线结构标准化失败，不影响问答返回。userId={}, sessionId={}",
                            request.getEffectiveUserId(), request.getEffectiveSessionId(), e);
                }
                if (result.getRoute() == null) {
                    routeRecommendService.recordChatRouteRequestWithoutPlan(
                            request,
                            request.getEffectiveUserId(),
                            request.getEffectiveSessionId(),
                            routeContext
                    );
                }
                chatPersistenceService.saveTextExchangeSafely(
                        request,
                        result,
                        request.getEffectiveUserId()
                );
                aiQuestionBehaviorService.recordAiQuestionSafely(
                        request.getVisitId(),
                        request.getEffectiveUserId(),
                        request.getEffectiveSessionId(),
                        request.getEffectiveParkId(),
                        request.getEffectiveParkName(),
                        request.getEffectiveScenicId(),
                        request.getEffectiveCurrentSpotName(),
                        request.getQuestion(),
                        request.getEffectiveInputType()
                );
                return result;
            }

            GuideChatResponse errorResponse = new GuideChatResponse();
            errorResponse.setAnswer("抱歉，AI服务暂时不可用，请稍后再试。");
            errorResponse.setVisitId(visitId);
            chatPersistenceService.saveTextExchangeSafely(
                    request,
                    errorResponse,
                    request.getEffectiveUserId()
            );
            return errorResponse;

        } catch (Exception e) {
            log.error("调用AI文本问答服务失败", e);
            GuideChatResponse errorResponse = new GuideChatResponse();
            errorResponse.setAnswer("抱歉，系统出现错误，请稍后再试。");
            errorResponse.setVisitId(visitId);
            chatPersistenceService.saveTextExchangeSafely(
                    request,
                    errorResponse,
                    request.getEffectiveUserId()
            );
            return errorResponse;
        }
    }

    /**
     * 确保当前用户存在有效的游览会话。
     * 如果已有进行中的会话（同一景区且未结束），则复用；否则创建新会话。
     */
    private String ensureVisitSession(GuideChatRequest request) {
        String userId = request.getEffectiveUserId();
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        String parkId = request.getEffectiveParkId();
        String parkName = request.getEffectiveParkName();
        String groupSize = request.getGroupSize();
        String travelType = request.getTravelType();
        String visitPreference = request.getVisitPreference();

        // 如果请求中已经带了 visitId 且不为空，可直接复用。
        if (hasText(request.getVisitId())) {
            return request.getVisitId();
        }

        if (!isOnsiteMode(request.getMode())) {
            return null;
        }

        // 调用 VisitService 获取或创建会话
        try {
            VisitStartResponse visitResp = visitService.getOrCreateVisit(
                    userId, parkId, parkName, groupSize, travelType, visitPreference,
                    request.getLongitude(), request.getLatitude()
            );
            if (visitResp != null && visitResp.visitId != null) {
                return String.valueOf(visitResp.visitId);
            }
        } catch (Exception e) {
            log.warn("自动创建游览会话失败，继续进行AI问答。userId={}, parkId={}", userId, parkId, e);
        }
        return null;
    }

    private boolean isOnsiteMode(String mode) {
        return "onsite".equalsIgnoreCase(firstNotBlank(mode));
    }

    private Map<String, Object> buildAiRequestBody(GuideChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        String userId = firstNotBlank(request.getEffectiveUserId());
        String areaCode = firstNotBlank(request.getAreaCode(), request.getParkId(), "");
        String parkId = firstNotBlank(request.getParkId(), areaCode, "");
        String areaName = firstNotBlank(request.getAreaName(), request.getScenicName(), request.getParkName(), "");
        String parkName = firstNotBlank(request.getParkName(), areaName, "");
        String currentSpotId = firstNotBlank(request.getEffectiveCurrentSpotId());
        String currentSpotName = firstNotBlank(request.getEffectiveCurrentSpotName());

        Boolean enableTts = request.getEffectiveEnableTts();
        Boolean enableContext = request.getEffectiveEnableContext();

        body.put("user_id", userId);
        body.put("question", request.getQuestion());
        body.put("input_type", firstNotBlank(request.getInputType(), "text"));
        body.put("voice", request.getEffectiveVoice());
        body.put("enable_tts", enableTts);
        body.put("enable_context", enableContext);

        putIfHasText(body, "session_id", request.getEffectiveSessionId());
        putIfHasText(body, "conversation_id", request.getConversationId());
        putIfHasText(body, "visit_id", request.getVisitId());
        putIfHasText(body, "mode", request.getMode());
        if (request.getAreaId() != null) {
            body.put("area_id", request.getAreaId());
        }
        putIfHasText(body, "park_id", parkId);
        putIfHasText(body, "park_name", parkName);
        putIfHasText(body, "scenic_id", currentSpotId);
        putIfHasText(body, "scenic_name", currentSpotName);
        putIfHasText(body, "area_code", areaCode);
        putIfHasText(body, "area_name", areaName);
        putIfHasText(body, "current_spot_id", currentSpotId);
        putIfHasText(body, "current_spot_name", currentSpotName);
        if (request.getRoute() != null) {
            body.put("route", request.getRoute());
        }
        if (request.getEffectiveProfile() != null) {
            body.put("profile", request.getEffectiveProfile());
        }
        if (request.getDigitalHumanConfig() != null) {
            body.put("digital_human_config", request.getDigitalHumanConfig());
        }
        putIfHasText(body, "voice_id", request.getVoiceId());
        putIfHasText(body, "avatar_id", request.getAvatarId());

        Map<String, Object> clientContext = request.getClientContext();
        if (clientContext == null) clientContext = new HashMap<>();
        clientContext.putIfAbsent("page", "guide");
        clientContext.putIfAbsent("location_text", firstNotBlank(currentSpotName, areaName, request.getScenicName(), ""));
        clientContext.putIfAbsent("lat", null);
        clientContext.putIfAbsent("lng", null);
        body.put("client_context", clientContext);

        return body;
    }

    @SuppressWarnings("unchecked")
    private GuideChatResponse parseAiResponse(Map<String, Object> responseBody) {
        Map<String, Object> dataMap = responseBody;
        Object dataObj = responseBody.get("data");
        if (dataObj instanceof Map<?, ?> dataMapRaw) {
            dataMap = (Map<String, Object>) dataMapRaw;
        }

        GuideChatResponse result = new GuideChatResponse();
        String recognizedText = readString(dataMap, "recognized_text", "recognizedText", "asr_text", "asrText", "questionText", "question_text");
        result.setRecognizedText(recognizedText);
        result.setQuestionText(firstNotBlank(recognizedText, readString(dataMap, "question")));
        result.setConversationId(readString(dataMap, "conversation_id", "conversationId"));
        result.setMessageId(readString(dataMap, "message_id", "messageId"));
        result.setAnswer(readString(dataMap, "answer", "content"));
        result.setRewrittenQuestion(readString(dataMap, "rewritten_question", "rewrittenQuestion"));
        result.setIntent(readString(dataMap, "intent"));
        result.setAudioUrl(readString(dataMap, "audio_url", "audioUrl"));
        result.setAudioFormat(readString(dataMap, "audio_format", "audioFormat"));
        result.setTtsError(readString(dataMap, "tts_error", "ttsError"));

        Object currentEntity = readObject(dataMap, "current_entity", "currentEntity");
        if (currentEntity instanceof Map<?, ?>) {
            result.setCurrentEntity(objectMapper.convertValue(currentEntity, new TypeReference<>() {}));
        }
        Object suggestions = readObject(dataMap, "suggestions");
        if (suggestions instanceof List<?>) {
            result.setSuggestions(objectMapper.convertValue(suggestions, new TypeReference<>() {}));
        }
        Object sources = readObject(dataMap, "sources");
        if (sources instanceof List<?>) {
            result.setSources(objectMapper.convertValue(sources, new TypeReference<>() {}));
        }
        Object mouthFrames = readObject(dataMap, "mouthFrames", "mouth_frames");
        if (mouthFrames instanceof List<?>) {
            result.setMouthFrames(objectMapper.convertValue(mouthFrames, new TypeReference<>() {}));
        }

        if (!hasText(result.getAnswer())) {
            result.setAnswer("已收到你的问题，后续这里将展示后端返回的真实答案。");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean containsRoutePayload(Map<String, Object> responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return false;
        }

        if (hasRouteKey(responseBody)) {
            return true;
        }

        Object dataObj = responseBody.get("data");
        if (dataObj instanceof Map<?, ?> dataMapRaw) {
            return hasRouteKey((Map<String, Object>) dataMapRaw);
        }
        return false;
    }

    private boolean hasRouteKey(Map<String, Object> map) {
        return map != null && (
                map.containsKey("route")
                        || map.containsKey("routePlan")
                        || map.containsKey("route_plan")
                        || map.containsKey("route_recommendation")
                        || map.containsKey("routeRecommendation")
                        || map.containsKey("recommended_spots")
                        || map.containsKey("recommendedSpots")
        );
    }

    private void putIfHasText(Map<String, Object> body, String key, String value) {
        if (hasText(value)) body.put(key, value);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Long parseLongOrNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return "";
    }

    private Object readObject(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
        }
        return null;
    }

    private String readString(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        return value == null ? null : String.valueOf(value);
    }
}
