package com.scenic.ai.modules.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.common.config.AiProperties;
import com.scenic.ai.modules.app.route.service.RouteRecommendService;
import com.scenic.ai.modules.app.visit.dto.VisitStartResponse;
import com.scenic.ai.modules.app.visit.service.VisitService;
import com.scenic.ai.modules.chat.dto.GuideChatRequest;
import com.scenic.ai.modules.chat.dto.GuideVoiceChatResponse;
import com.scenic.ai.modules.chat.dto.MouthFrameDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GuideVoiceChatService {

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiQuestionBehaviorService aiQuestionBehaviorService;
    private final VisitService visitService;  // 注入
    private final ChatPersistenceService chatPersistenceService;
    private final RouteRecommendService routeRecommendService;

    public GuideVoiceChatService(RestTemplate restTemplate,
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

    public GuideVoiceChatResponse callVoiceAiService(MultipartFile audio,
                                                     String question,
                                                     String sessionId,
                                                     String scenicName,
                                                     String visitId,
                                                     String userId,
                                                     String loginUserId,
                                                     String visitorId,
                                                     String conversationId,
                                                     String areaCode,
                                                     String areaName,
                                                     Long areaId,
                                                     String sceneCode,
                                                     String sceneName,
                                                     String currentSpotId,
                                                     String currentSpotName,
                                                     String voice,
                                                     Boolean enableContext,
                                                     Boolean enableTts,
                                                     String inputType,
                                                     String groupSize,      // 新增
                                                     String travelType,     // 新增
                                                     String visitPreference, // 新增
                                                     Boolean route,
                                                     Object profile,
                                                     Object digitalHumanConfig,
                                                     String voiceId,
                                                     String avatarId,
                                                     String routeStartType,
                                                     String mode
    ) {
        // ========== 1. 确保游览会话存在 ==========
        String finalUserId = firstNotBlank(userId, loginUserId, visitorId);
        String finalVisitId = ensureVisitSession(finalUserId, areaCode, areaName, groupSize, travelType, visitPreference, visitId, mode);
        // =====================================

        GuideVoiceChatResponse result = new GuideVoiceChatResponse();
        result.setVisitId(finalVisitId);  // 返回给 Android
        String finalSessionId = chatPersistenceService.ensureSessionId(sessionId, conversationId);
        String finalConversationId = firstNotBlank(conversationId, finalSessionId);
        String finalAreaName = firstNotBlank(areaName, scenicName, sceneName, "");
        String finalScenicName = firstNotBlank(scenicName, currentSpotName, sceneName, finalAreaName, "通用导览");
        String finalSceneCode = firstNotBlank(sceneCode, currentSpotId, "");
        String finalSceneName = firstNotBlank(sceneName, currentSpotName, "");
        String finalCurrentSpotId = firstNotBlank(currentSpotId, sceneCode, "");
        String finalCurrentSpotName = firstNotBlank(currentSpotName, sceneName, "");
        Boolean finalEnableContext = enableContext == null ? true : enableContext;
        Boolean finalEnableTts = enableTts == null ? true : enableTts;
        String finalVoice = firstNotBlank(voice, "zhitian_emo");
        String finalVoiceId = firstNotBlank(voiceId, finalVoice);
        String finalInputType = firstNotBlank(inputType, "voice");
        String finalQuestion = firstNotBlank(question);

        try {
            String url = aiProperties.getBaseUrl() + aiProperties.getTextChatEndpoint();
            log.info("调用语音问答服务: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            Map<String, Object> routeBody = new LinkedHashMap<>();

            ByteArrayResource audioResource = new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() {
                    return audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "voice.webm";
                }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentDispositionFormData("audio_file", audioResource.getFilename());
            String contentType = audio.getContentType();
            fileHeaders.setContentType(hasText(contentType) ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM);
            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(audioResource, fileHeaders);
            body.add("audio_file", filePart);

            putIfHasText(routeBody, "question", finalQuestion);
            putIfHasText(routeBody, "user_id", finalUserId);
            putIfHasText(routeBody, "login_user_id", loginUserId);
            putIfHasText(routeBody, "visitor_id", visitorId);
            putIfHasText(routeBody, "session_id", finalSessionId);
            putIfHasText(routeBody, "conversation_id", finalConversationId);
            putIfHasText(routeBody, "visit_id", finalVisitId);
            if (areaId != null) {
                routeBody.put("area_id", areaId);
            }
            routeBody.put("input_type", finalInputType);
            routeBody.put("voice", finalVoice);
            routeBody.put("voice_id", finalVoiceId);
            putIfHasText(routeBody, "avatar_id", avatarId);
            routeBody.put("enable_context", finalEnableContext);
            routeBody.put("enable_tts", finalEnableTts);
            routeBody.put("need_voice", finalEnableTts);
            if (route != null) {
                routeBody.put("route", route);
            }
            putIfHasText(routeBody, "mode", mode);
            putIfHasText(routeBody, "area_code", areaCode);
            putIfHasText(routeBody, "park_id", areaCode);
            putIfHasText(routeBody, "area_name", finalAreaName);
            putIfHasText(routeBody, "park_name", finalAreaName);
            putIfHasText(routeBody, "scenicName", finalScenicName);
            putIfHasText(routeBody, "scene_code", finalSceneCode);
            putIfHasText(routeBody, "scenic_id", finalSceneCode);
            putIfHasText(routeBody, "scene_name", finalSceneName);
            putIfHasText(routeBody, "current_spot_id", finalCurrentSpotId);
            putIfHasText(routeBody, "current_spot_name", finalCurrentSpotName);
            if (profile != null) {
                routeBody.put("profile", profile);
            }
            if (digitalHumanConfig != null) {
                routeBody.put("digital_human_config", digitalHumanConfig);
            }

            GuideChatRequest routeRequest = buildRouteGuideChatRequest(
                    finalUserId,
                    finalSessionId,
                    finalVisitId,
                    areaId,
                    areaCode,
                    finalAreaName,
                    finalSceneCode,
                    finalSceneName,
                    finalCurrentSpotId,
                    finalCurrentSpotName,
                    finalQuestion,
                    route,
                    routeStartType,
                    profile,
                    digitalHumanConfig,
                    finalVoiceId,
                    avatarId,
                    mode
            );
            RouteRecommendService.ChatRouteContext routeContext = null;
            try {
                routeContext = routeRecommendService.enrichChatRouteRequest(routeRequest, finalUserId, routeBody);
            } catch (Exception e) {
                log.warn("语音Chat路线请求增强失败，继续普通语音问答。userId={}, sessionId={}",
                        finalUserId, finalSessionId, e);
            }
            addMultipartFields(body, routeBody);

            log.info("语音问答请求字段: user_id={}, visit_id={}, groupSize={}, travelType={}, visitPreference={}",
                    finalUserId, finalVisitId, groupSize, travelType, visitPreference);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("语音问答服务响应: {}", responseBody);
                GuideVoiceChatResponse parsedResponse = parseVoiceAiResponse(responseBody);
                if (!hasText(parsedResponse.getQuestionText()) && hasText(finalQuestion)) {
                    parsedResponse.setQuestionText(finalQuestion);
                }
                if (!hasText(parsedResponse.getRecognizedText()) && hasText(parsedResponse.getQuestionText())) {
                    parsedResponse.setRecognizedText(parsedResponse.getQuestionText());
                }
                parsedResponse.setVisitId(finalVisitId);
                if (containsRoutePayload(responseBody) && !Boolean.TRUE.equals(routeRequest.getRoute())) {
                    routeRequest.setRoute(true);
                    routeRequest.setQuestion(firstNotBlank(parsedResponse.getQuestionText(), finalQuestion, "请为我推荐一条合理的游览路线。"));
                    routeContext = routeRecommendService.enrichChatRouteRequest(
                            routeRequest,
                            finalUserId,
                            new LinkedHashMap<>(routeBody)
                    );
                }
                try {
                    parsedResponse.setRoute(routeRecommendService.standardizeAndSaveChatRoute(
                            routeRequest,
                            finalUserId,
                            finalSessionId,
                            responseBody,
                            parsedResponse.getAnswer(),
                            routeContext
                    ));
                } catch (Exception e) {
                    log.warn("语音Chat路线结构标准化失败，不影响问答返回。userId={}, sessionId={}",
                            finalUserId, finalSessionId, e);
                }
                if (parsedResponse.getRoute() == null) {
                    routeRecommendService.recordChatRouteRequestWithoutPlan(
                            routeRequest,
                            finalUserId,
                            finalSessionId,
                            routeContext
                    );
                }
                chatPersistenceService.saveVoiceExchangeSafely(
                        finalSessionId,
                        finalUserId,
                        areaId,
                        areaCode,
                        finalAreaName,
                        finalCurrentSpotId,
                        firstNotBlank(finalCurrentSpotName, finalSceneName, finalScenicName),
                        finalInputType,
                        parsedResponse
                );
                aiQuestionBehaviorService.recordAiQuestionSafely(
                        finalVisitId,
                        finalUserId,
                        finalSessionId,
                        areaCode,
                        finalAreaName,
                        finalSceneCode,
                        firstNotBlank(finalCurrentSpotName, finalSceneName, finalScenicName),
                        parsedResponse.getQuestionText(),
                        finalInputType
                );
                return parsedResponse;
            }

            result.setAnswer("抱歉，语音问答服务暂时不可用，请稍后再试。");
            chatPersistenceService.saveVoiceExchangeSafely(
                    finalSessionId,
                    finalUserId,
                    areaId,
                    areaCode,
                    finalAreaName,
                    finalCurrentSpotId,
                    firstNotBlank(finalCurrentSpotName, finalSceneName, finalScenicName),
                    finalInputType,
                    result
            );
            return result;

        } catch (Exception e) {
            log.error("调用语音问答服务失败", e);
            result.setAnswer("抱歉，语音问答服务暂时不可用，请稍后再试。");
            chatPersistenceService.saveVoiceExchangeSafely(
                    finalSessionId,
                    finalUserId,
                    areaId,
                    areaCode,
                    finalAreaName,
                    finalCurrentSpotId,
                    firstNotBlank(finalCurrentSpotName, finalSceneName, finalScenicName),
                    finalInputType,
                    result
            );
            return result;
        }
    }

    /**
     * 确保游览会话存在，如果已经有 visitId 且有效则复用，否则创建新会话。
     */
    private String ensureVisitSession(String userId, String parkId, String parkName,
                                      String groupSize, String travelType, String visitPreference,
                                      String existingVisitId,
                                      String mode) {
        if (hasText(existingVisitId)) {
            return existingVisitId;
        }
        if (!isOnsiteMode(mode)) {
            return null;
        }
        if (!hasText(userId) || !hasText(parkId)) {
            return null;
        }
        try {
            VisitStartResponse resp = visitService.getOrCreateVisit(
                    userId, parkId, parkName, groupSize, travelType, visitPreference,
                    null, null
            );
            return resp != null && resp.visitId != null ? String.valueOf(resp.visitId) : null;
        } catch (Exception e) {
            log.warn("自动创建语音游览会话失败，继续进行AI问答。userId={}, parkId={}", userId, parkId, e);
            return null;
        }
    }

    private GuideChatRequest buildRouteGuideChatRequest(
            String userId,
            String sessionId,
            String visitId,
            Long areaId,
            String areaCode,
            String areaName,
            String sceneCode,
            String sceneName,
            String currentSpotId,
            String currentSpotName,
            String question,
            Boolean route,
            String routeStartType,
            Object profile,
            Object digitalHumanConfig,
            String voiceId,
            String avatarId,
            String mode
    ) {
        GuideChatRequest request = new GuideChatRequest();
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setConversationId(sessionId);
        request.setVisitId(visitId);
        request.setAreaId(areaId);
        request.setAreaCode(areaCode);
        request.setParkId(areaCode);
        request.setAreaName(areaName);
        request.setParkName(areaName);
        request.setScenicId(sceneCode);
        request.setSceneCode(sceneCode);
        request.setSceneName(sceneName);
        request.setCurrentSpotId(currentSpotId);
        request.setCurrentSpotName(currentSpotName);
        request.setSpotId(currentSpotId);
        request.setSpotName(currentSpotName);
        request.setQuestion(question);
        request.setInputType("voice");
        request.setRoute(route);
        request.setRouteStartType(routeStartType);
        request.setProfile(profile);
        request.setDigitalHumanConfig(digitalHumanConfig);
        request.setVoiceId(voiceId);
        request.setAvatarId(avatarId);
        request.setMode(mode);
        request.setEnableTts(true);
        request.setEnableContext(true);
        return request;
    }

    private boolean isOnsiteMode(String mode) {
        return "onsite".equalsIgnoreCase(firstNotBlank(mode));
    }

    private void putIfHasText(Map<String, Object> body, String key, String value) {
        if (hasText(value)) {
            body.put(key, value.trim());
        }
    }

    private void addMultipartFields(MultiValueMap<String, Object> body, Map<String, Object> values) {
        if (body == null || values == null || values.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String value = toMultipartText(entry.getValue());
            if (hasText(value)) {
                body.add(entry.getKey(), value);
            }
        }
    }

    private String toMultipartText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text.trim();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private GuideVoiceChatResponse parseVoiceAiResponse(Map<String, Object> responseBody) {
        Map<String, Object> dataMap = responseBody;
        Object dataObj = responseBody.get("data");
        if (dataObj instanceof Map<?, ?> dataMapRaw) {
            dataMap = (Map<String, Object>) dataMapRaw;
        }

        GuideVoiceChatResponse result = new GuideVoiceChatResponse();
        String rawRecognizedText = readString(
                dataMap,
                "recognized_text",
                "recognizedText",
                "asr_text",
                "asrText",
                "questionText",
                "question_text",
                "question"
        );
        String recognizedText = correctQuestionText(rawRecognizedText);
        result.setRecognizedText(recognizedText);
        result.setQuestionText(recognizedText);
        result.setConversationId(readString(dataMap, "conversation_id", "conversationId"));
        result.setMessageId(readString(dataMap, "message_id", "messageId"));
        result.setAnswer(readString(dataMap, "answer", "content"));
        result.setRewrittenQuestion(readString(dataMap, "rewritten_question", "rewrittenQuestion"));
        result.setIntent(readString(dataMap, "intent"));
        result.setAudioUrl(readString(dataMap, "audioUrl", "audio_url"));
        result.setAudioFormat(readString(dataMap, "audioFormat", "audio_format"));
        result.setTtsError(readString(dataMap, "ttsError", "tts_error"));

        Object currentEntity = readObject(dataMap, "current_entity", "currentEntity");
        if (currentEntity instanceof Map<?, ?>) {
            result.setCurrentEntity(objectMapper.convertValue(currentEntity, new TypeReference<>() {}));
        }
        Object mouthFrames = readObject(dataMap, "mouthFrames", "mouth_frames");
        if (mouthFrames instanceof List<?>) {
            result.setMouthFrames(objectMapper.convertValue(mouthFrames, new TypeReference<>() {}));
        }
        Object suggestions = readObject(dataMap, "suggestions");
        if (suggestions instanceof List<?>) {
            result.setSuggestions(objectMapper.convertValue(suggestions, new TypeReference<>() {}));
        }
        Object sources = readObject(dataMap, "sources");
        if (sources instanceof List<?>) {
            result.setSources(objectMapper.convertValue(sources, new TypeReference<>() {}));
        }
        if (!hasText(result.getAnswer())) {
            result.setAnswer("已收到你的语音问题，后续这里将展示后端返回的真实答案。");
        }
        return result;
    }

    private String correctQuestionText(String text) {
        if (!hasText(text)) return text;
        String result = text.trim().replaceAll("\\s+", "")
                .replace('？', '?').replace('。', '.').replace('，', ',')
                .replaceAll("^[,，。.!！?？]+", "").replaceAll("[,，。.!！?？]+$", "");
        result = result.replace("祥福禅寺", "祥符禅寺").replace("祥符产寺", "祥符禅寺").replace("祥符禅四", "祥符禅寺");
        result = result.replace("灵山圣境", "灵山胜境").replace("灵山圣镜", "灵山胜境").replace("零山胜境", "灵山胜境");
        result = result.replace("年花堂", "拈花堂").replace("连花堂", "拈花堂").replace("念花堂", "拈花堂");
        result = result.replace("九龙贯浴", "九龙灌浴").replace("九龙江浴", "九龙灌浴");
        result = result.replace("降魔铜相", "降魔铜像").replace("降魔同像", "降魔铜像");
        result = result.replace("洗手间在哪", "洗手间在哪里").replace("厕所在哪", "洗手间在哪里").replace("卫生间在哪", "洗手间在哪里");
        result = result.replace("怎么走啊", "怎么走").replace("怎么去啊", "怎么去");
        return result.trim();
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
