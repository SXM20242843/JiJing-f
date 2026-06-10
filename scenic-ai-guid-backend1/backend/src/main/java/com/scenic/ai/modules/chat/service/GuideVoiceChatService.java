package com.scenic.ai.modules.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.common.config.AiProperties;
import com.scenic.ai.modules.app.route.service.RouteRecommendService;
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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GuideVoiceChatService {

    private static final long TTS_WAIT_TIMEOUT_MS = 4000L;
    private static final long TTS_WAIT_INTERVAL_MS = 500L;
    private static final int TTS_WAIT_MAX_ATTEMPTS = 8;

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
            boolean allowRoute = isExplicitRouteRequest(route, routeStartType, finalQuestion);
            if (allowRoute && !Boolean.TRUE.equals(route)) {
                routeBody.put("route", true);
                routeRequest.setRoute(true);
            }

            RouteRecommendService.ChatRouteContext routeContext = null;
            if (allowRoute) {
                try {
                    routeContext = routeRecommendService.enrichChatRouteRequest(routeRequest, finalUserId, routeBody);
                } catch (Exception e) {
                    log.warn("语音Chat路线请求增强失败，继续普通语音问答。userId={}, sessionId={}",
                            finalUserId, finalSessionId, e);
                }
            }
            applyDigitalHumanOptions(routeBody);
            applyRoutePayload(routeBody, routeRequest, allowRoute);
            addMultipartFields(body, routeBody);

            log.info("语音问答请求字段: user_id={}, visit_id={}, groupSize={}, travelType={}, visitPreference={}",
                    finalUserId, finalVisitId, groupSize, travelType, visitPreference);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("语音问答服务响应成功: status={}, responseKeys={}",
                        response.getStatusCode(), responseBody.keySet());
                GuideVoiceChatResponse parsedResponse = parseVoiceAiResponse(responseBody);
                if (!hasText(parsedResponse.getQuestionText()) && hasText(finalQuestion)) {
                    parsedResponse.setQuestionText(finalQuestion);
                }
                if (!hasText(parsedResponse.getRecognizedText()) && hasText(parsedResponse.getQuestionText())) {
                    parsedResponse.setRecognizedText(parsedResponse.getQuestionText());
                }
                parsedResponse.setVisitId(finalVisitId);
                parsedResponse = waitForTtsIfPending(parsedResponse);

                String responseQuestion = firstNotBlank(
                        parsedResponse.getQuestionText(),
                        parsedResponse.getRecognizedText(),
                        finalQuestion
                );
                boolean responseAllowRoute = allowRoute || hasRouteIntentText(responseQuestion);

                if (responseAllowRoute) {
                    routeRequest.setRoute(true);
                    routeRequest.setQuestion(firstNotBlank(responseQuestion, "请为我推荐一条合理的游览路线。"));

                    if (routeContext == null || containsRoutePayload(responseBody)) {
                        try {
                            routeContext = routeRecommendService.enrichChatRouteRequest(
                                    routeRequest,
                                    finalUserId,
                                    new LinkedHashMap<>(routeBody)
                            );
                        } catch (Exception e) {
                            log.warn("语音Chat路线响应后增强失败，继续普通语音问答。userId={}, sessionId={}",
                                    finalUserId, finalSessionId, e);
                        }
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
                } else if (containsRoutePayload(responseBody)) {
                    log.info("非路线语音意图忽略 AI 返回 route，不保存路线。userId={}, sessionId={}",
                            finalUserId, finalSessionId);
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
     * 仅复用请求中已有的 visitId。
     * /api/guide/voice/chat 不负责创建 tourist_visit_session，缺少 visitId 时继续普通语音问答。
     */
    private String ensureVisitSession(String userId, String parkId, String parkName,
                                      String groupSize, String travelType, String visitPreference,
                                      String existingVisitId,
                                      String mode) {
        if (hasText(existingVisitId)) {
            return existingVisitId.trim();
        }

        log.info("GuideVoiceChat 未携带 visitId，不自动创建游览会话。userId={}, mode={}, parkId={}",
                userId, mode, parkId);
        return null;
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

    private void applyDigitalHumanOptions(Map<String, Object> body) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("responseMode", "digital_human");
        options.put("enableTts", true);
        options.put("ttsMode", "async");
        options.put("includeMouthFrames", true);
        options.put("includeSources", false);
        options.put("includeDebug", false);
        body.put("options", options);
    }

    private void applyRoutePayload(Map<String, Object> body, GuideChatRequest request, boolean allowRoute) {
        Map<String, Object> routePayload = new LinkedHashMap<>();
        routePayload.put("enabled", allowRoute);

        if (allowRoute) {
            Integer availableMinutes = request.getAvailableMinutes();
            if (availableMinutes == null) {
                availableMinutes = readInteger(readObject(body, "available_minutes", "availableMinutes"));
            }
            if (availableMinutes != null) {
                routePayload.put("availableMinutes", availableMinutes);
            }
            routePayload.put("preferenceTags",
                    request.getPreferenceTags() == null ? new ArrayList<String>() : request.getPreferenceTags());
            routePayload.put("avoidTags", new ArrayList<String>());
            body.put("route", routePayload);
            body.put("route_enabled", true);
            return;
        }

        body.put("route", routePayload);
        body.put("route_enabled", false);
        body.put("suppress_route", true);
    }

    private boolean isExplicitRouteRequest(Boolean route, String routeStartType, String question) {
        if (Boolean.TRUE.equals(route)) {
            return true;
        }

        String startType = firstNotBlank(routeStartType);
        if ("manual".equalsIgnoreCase(startType)
                || "recommend".equalsIgnoreCase(startType)
                || "nearby".equalsIgnoreCase(startType)) {
            return true;
        }

        return hasRouteIntentText(question);
    }

    private boolean hasRouteIntentText(String question) {
        String text = firstNotBlank(question);
        if (text.isEmpty()) {
            return false;
        }

        return text.contains("推荐路线")
                || text.contains("规划路线")
                || text.contains("游览路线")
                || text.contains("路线规划")
                || text.contains("怎么走")
                || text.contains("导航")
                || text.contains("怎么逛")
                || text.contains("如何逛")
                || text.contains("游览顺序")
                || text.contains("接下来去哪")
                || text.contains("下一站去哪");
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

    private GuideVoiceChatResponse waitForTtsIfPending(GuideVoiceChatResponse result) {
        if (result == null || hasText(result.getAudioUrl())) {
            return result;
        }

        String taskId = firstNotBlank(result.getTtsTaskId(), result.getTaskId());
        if (!"PENDING".equalsIgnoreCase(firstNotBlank(result.getAudioStatus())) || !hasText(taskId)) {
            return result;
        }

        if (!hasText(aiProperties.getTtsStatusEndpoint())) {
            log.info("[TtsWait] no AI task result endpoint configured, cannot wait final audio");
            return result;
        }

        log.info("[TtsWait] pending taskId={}, start wait", taskId);
        long deadline = System.currentTimeMillis() + TTS_WAIT_TIMEOUT_MS;
        for (int attempt = 1; attempt <= TTS_WAIT_MAX_ATTEMPTS; attempt++) {
            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0L) {
                break;
            }
            try {
                Thread.sleep(Math.min(TTS_WAIT_INTERVAL_MS, remainingMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[TtsWait] timeout taskId={}, keep PENDING", taskId);
                return result;
            }

            GuideVoiceChatResponse polled = queryTtsStatus(taskId, result.getMessageId(), result.getConversationId());
            int frameCount = polled.getMouthFrames() == null ? 0 : polled.getMouthFrames().size();
            log.info("[TtsWait] poll attempt={}, status={}, audioUrl={}, mouthFrames={}",
                    attempt,
                    polled.getAudioStatus(),
                    polled.getAudioUrl(),
                    frameCount);
            if (hasText(polled.getAudioUrl())) {
                mergeTtsResult(result, polled);
                hydrateDigitalHumanPayloads(result);
                log.info("[TtsWait] success taskId={}, audioUrl={}, frames={}",
                        taskId,
                        result.getAudioUrl(),
                        result.getMouthFrames() == null ? 0 : result.getMouthFrames().size());
                return result;
            }
        }

        result.setTtsTaskId(taskId);
        result.setTaskId(firstNotBlank(result.getTaskId(), taskId));
        hydrateDigitalHumanPayloads(result);
        log.info("[TtsWait] timeout taskId={}, keep PENDING", taskId);
        return result;
    }

    private GuideVoiceChatResponse queryTtsStatus(String taskId, String messageId, String conversationId) {
        GuideVoiceChatResponse result = new GuideVoiceChatResponse();
        result.setTtsTaskId(firstNotBlank(taskId));
        result.setTaskId(firstNotBlank(taskId));

        if (!hasText(taskId)) {
            result.setAudioStatus("FAILED");
            result.setMouthStatus("FAILED");
            hydrateDigitalHumanPayloads(result);
            return result;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = buildTtsStatusUrl(taskId, messageId, conversationId);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                result = parseVoiceAiResponse(response.getBody());
                if (!hasText(result.getTtsTaskId())) {
                    result.setTtsTaskId(taskId.trim());
                }
                if (!hasText(result.getTaskId())) {
                    result.setTaskId(result.getTtsTaskId());
                }
                hydrateDigitalHumanPayloads(result);
                return result;
            }
        } catch (Exception e) {
            log.warn("查询 AI TTS 状态失败: taskId={}", taskId, e);
        }

        result.setAudioStatus("FAILED");
        result.setMouthStatus("FAILED");
        hydrateDigitalHumanPayloads(result);
        return result;
    }

    private void mergeTtsResult(GuideVoiceChatResponse target, GuideVoiceChatResponse source) {
        if (target == null || source == null) {
            return;
        }
        target.setAudioUrl(source.getAudioUrl());
        target.setAudioStatus("SUCCESS");
        if (hasText(source.getAudioFormat())) {
            target.setAudioFormat(source.getAudioFormat());
        }
        if (source.getAudioDurationMs() != null) {
            target.setAudioDurationMs(source.getAudioDurationMs());
        }
        if (hasText(source.getTtsTaskId())) {
            target.setTtsTaskId(source.getTtsTaskId());
        }
        if (hasText(source.getTaskId())) {
            target.setTaskId(source.getTaskId());
        }
        if (hasText(source.getTtsError())) {
            target.setTtsError(source.getTtsError());
        }
        if (!hasText(target.getMessageId()) && hasText(source.getMessageId())) {
            target.setMessageId(source.getMessageId());
        }
        if (!hasText(target.getConversationId()) && hasText(source.getConversationId())) {
            target.setConversationId(source.getConversationId());
        }
        if (source.getMouthFrames() != null && !source.getMouthFrames().isEmpty()) {
            target.setMouthFrames(source.getMouthFrames());
            target.setMouthStatus(firstNotBlank(source.getMouthStatus(), "SUCCESS"));
        } else if (hasText(source.getMouthStatus())) {
            target.setMouthStatus(source.getMouthStatus());
        }
    }

    private String buildTtsStatusUrl(String taskId, String messageId, String conversationId) {
        String endpoint = firstNotBlank(aiProperties.getTtsStatusEndpoint(), "/api/chat/tts/status");
        String lowerEndpoint = endpoint.toLowerCase();
        String baseUrl = lowerEndpoint.startsWith("http://") || lowerEndpoint.startsWith("https://")
                ? endpoint
                : trimTrailingSlash(aiProperties.getBaseUrl()) + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        StringBuilder url = new StringBuilder(baseUrl);
        appendQueryParam(url, "taskId", taskId);
        appendQueryParam(url, "messageId", messageId);
        appendQueryParam(url, "conversationId", conversationId);
        return url.toString();
    }

    private void appendQueryParam(StringBuilder url, String key, String value) {
        if (url == null || !hasText(key) || !hasText(value)) {
            return;
        }
        url.append(url.indexOf("?") >= 0 ? "&" : "?")
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private GuideVoiceChatResponse parseVoiceAiResponse(Map<String, Object> responseBody) {
        Map<String, Object> rootMap = responseBody == null ? new LinkedHashMap<>() : responseBody;
        Map<String, Object> dataMap = rootMap;
        Object dataObj = rootMap.get("data");
        if (dataObj instanceof Map<?, ?> dataMapRaw) {
            dataMap = (Map<String, Object>) dataMapRaw;
        }

        Map<String, Object> audioMap = readMapCascade(dataMap, rootMap,
                "audio", "tts", "voice", "speech", "audioData", "audio_data");
        Map<String, Object> mouthMap = readMapCascade(dataMap, rootMap,
                "mouth", "mouthSync", "mouth_sync", "lipSync", "lip_sync", "mouthData", "mouth_data");
        Map<String, Object> asrMap = readMapCascade(dataMap, rootMap,
                "asr", "speechRecognition", "speech_recognition", "recognition", "recognized", "transcription");
        Map<String, Object> digitalHumanMap = readMapCascade(dataMap, rootMap,
                "digitalHuman", "digital_human");

        GuideVoiceChatResponse result = new GuideVoiceChatResponse();

        String rawRecognizedText = firstNotBlank(
                readStringCascade(dataMap, rootMap,
                        "recognized_text",
                        "recognizedText",
                        "asr_text",
                        "asrText",
                        "questionText",
                        "question_text",
                        "question"),
                readString(asrMap,
                        "text",
                        "result",
                        "recognized_text",
                        "recognizedText",
                        "asr_text",
                        "asrText",
                        "questionText",
                        "question_text",
                        "question")
        );
        String recognizedText = correctQuestionText(rawRecognizedText);
        result.setRecognizedText(recognizedText);
        result.setQuestionText(recognizedText);

        result.setConversationId(readStringCascade(dataMap, rootMap, "conversation_id", "conversationId"));
        result.setMessageId(readStringCascade(dataMap, rootMap, "message_id", "messageId"));
        result.setAnswer(readStringCascade(dataMap, rootMap, "answer", "content", "text"));
        result.setRewrittenQuestion(readStringCascade(dataMap, rootMap, "rewritten_question", "rewrittenQuestion"));
        result.setIntent(readStringCascade(dataMap, rootMap, "intent"));

        String audioUrl = firstNotBlank(
                readStringCascade(dataMap, rootMap,
                        "audioUrl",
                        "audio_url",
                        "audioURL",
                        "ttsUrl",
                        "tts_url",
                        "voiceUrl",
                        "voice_url",
                        "speechUrl",
                        "speech_url"),
                readString(audioMap,
                        "url",
                        "audioUrl",
                        "audio_url",
                        "audioURL",
                        "ttsUrl",
                        "tts_url",
                        "voiceUrl",
                        "voice_url",
                        "speechUrl",
                        "speech_url",
                        "fileUrl",
                        "file_url")
        );
        result.setAudioUrl(normalizeAudioUrl(audioUrl));
        result.setAudioFormat(firstNotBlank(
                readStringCascade(dataMap, rootMap, "audioFormat", "audio_format", "format"),
                readString(audioMap, "format", "audioFormat", "audio_format", "mime", "mimeType", "mime_type")
        ));
        result.setAudioStatus(firstNotBlank(
                readStringCascade(dataMap, rootMap, "audioStatus", "audio_status", "ttsStatus", "tts_status"),
                readString(audioMap, "status", "audioStatus", "audio_status", "ttsStatus", "tts_status")
        ));
        result.setTtsTaskId(firstNotBlank(
                readStringCascade(dataMap, rootMap, "ttsTaskId", "tts_task_id", "taskId", "task_id"),
                readString(audioMap, "taskId", "task_id")
        ));
        result.setTaskId(firstNotBlank(result.getTtsTaskId()));
        result.setAudioDurationMs(firstNonNullLong(
                readLong(dataMap, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms"),
                readLong(rootMap, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms"),
                readLong(audioMap, "durationMs", "duration_ms")
        ));
        result.setTtsError(firstNotBlank(
                readStringCascade(dataMap, rootMap, "ttsError", "tts_error", "error", "errorMessage", "error_message"),
                readString(audioMap, "ttsError", "tts_error", "error", "errorMessage", "error_message")
        ));

        Object currentEntity = firstNonNull(
                readObject(dataMap, "current_entity", "currentEntity"),
                readObject(rootMap, "current_entity", "currentEntity")
        );
        if (currentEntity instanceof Map<?, ?>) {
            result.setCurrentEntity(objectMapper.convertValue(currentEntity, new TypeReference<>() {}));
        }

        Object mouthFrames = firstNonNull(
                readObject(dataMap,
                        "mouthFrames",
                        "mouth_frames",
                        "mouthFrameList",
                        "mouth_frame_list",
                        "lipFrames",
                        "lip_frames"),
                readObject(rootMap,
                        "mouthFrames",
                        "mouth_frames",
                        "mouthFrameList",
                        "mouth_frame_list",
                        "lipFrames",
                        "lip_frames"),
                readObject(mouthMap,
                        "frames",
                        "mouthFrames",
                        "mouth_frames",
                        "mouthFrameList",
                        "mouth_frame_list",
                        "lipFrames",
                        "lip_frames"),
                readObject(audioMap,
                        "mouthFrames",
                        "mouth_frames",
                        "mouthFrameList",
                        "mouth_frame_list",
                        "lipFrames",
                        "lip_frames")
        );
        if (mouthFrames instanceof List<?>) {
            result.setMouthFrames(normalizeMouthFrames((List<?>) mouthFrames));
        }
        result.setMouthStatus(firstNotBlank(
                readStringCascade(dataMap, rootMap, "mouthStatus", "mouth_status"),
                readString(mouthMap, "status", "mouthStatus", "mouth_status")
        ));
        result.setEmotion(firstNotBlank(
                readStringCascade(dataMap, rootMap, "emotion", "emotion_code", "emotionCode"),
                readString(digitalHumanMap, "emotion")
        ));
        result.setEmotionCode(firstNotBlank(
                readStringCascade(dataMap, rootMap, "emotion_code", "emotionCode"),
                readString(digitalHumanMap, "emotionCode", "emotion_code")
        ));
        result.setAction(firstNotBlank(
                readStringCascade(dataMap, rootMap, "action", "action_code", "actionCode"),
                readString(digitalHumanMap, "action")
        ));
        result.setActionCode(firstNotBlank(
                readStringCascade(dataMap, rootMap, "action_code", "actionCode"),
                readString(digitalHumanMap, "actionCode", "action_code")
        ));
        result.setAvatarId(firstNotBlank(
                readStringCascade(dataMap, rootMap, "avatar_id", "avatarId"),
                readString(digitalHumanMap, "avatarId", "avatar_id")
        ));

        result.setAudio(buildAudioPayload(result));
        result.setMouth(buildMouthPayload(result));
        result.setDigitalHuman(buildDigitalHumanPayload(result));
        hydrateDigitalHumanPayloads(result);

        Object suggestions = firstNonNull(
                readObject(dataMap, "suggestions"),
                readObject(rootMap, "suggestions")
        );
        if (suggestions instanceof List<?>) {
            result.setSuggestions(objectMapper.convertValue(suggestions, new TypeReference<>() {}));
        }

        Object sources = firstNonNull(
                readObject(dataMap, "sources"),
                readObject(rootMap, "sources")
        );
        if (sources instanceof List<?>) {
            result.setSources(objectMapper.convertValue(sources, new TypeReference<>() {}));
        }

        if (!hasText(result.getAnswer())) {
            String fallbackMsg = readStringCascade(rootMap, dataMap, "msg", "message");
            result.setAnswer(hasText(fallbackMsg) ? fallbackMsg : "已收到你的语音问题，后续这里将展示后端返回的真实答案。");
        }

        log.info("语音AI响应解析结果: questionText={}, answerLen={}, audioUrl={}, mouthFrames={}",
                result.getQuestionText(),
                result.getAnswer() == null ? 0 : result.getAnswer().length(),
                result.getAudioUrl(),
                result.getMouthFrames() == null ? 0 : result.getMouthFrames().size());
        logDigitalHumanDebug(audioMap, mouthMap, digitalHumanMap, rootMap, result);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMapCascade(Map<String, Object> primary,
                                               Map<String, Object> fallback,
                                               String... keys) {
        Object object = firstNonNull(readObject(primary, keys), readObject(fallback, keys));
        if (object instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        return null;
    }

    private Object readObject(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || !map.containsKey(key)) {
                continue;
            }
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String readStringCascade(Map<String, Object> primary,
                                     Map<String, Object> fallback,
                                     String... keys) {
        return firstNotBlank(readString(primary, keys), readString(fallback, keys));
    }

    private String readString(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        return scalarToText(value);
    }

    private Long readLong(Map<String, Object> map, String... keys) {
        return readLongValue(readObject(map, keys));
    }

    private Long readLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = scalarToText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer readInteger(Object value) {
        Long longValue = readLongValue(value);
        return longValue == null ? null : longValue.intValue();
    }

    private Double readDouble(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = scalarToText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String scalarToText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return "";
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()
                || "null".equalsIgnoreCase(text)
                || "undefined".equalsIgnoreCase(text)
                || "none".equalsIgnoreCase(text)) {
            return "";
        }
        return text;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long firstNonNullLong(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<MouthFrameDto> normalizeMouthFrames(List<?> frames) {
        List<MouthFrameDto> normalized = new ArrayList<>();
        if (frames == null || frames.isEmpty()) {
            return normalized;
        }
        for (Object frame : frames) {
            if (!(frame instanceof Map<?, ?> frameMapRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> frameMap = (Map<String, Object>) frameMapRaw;
            MouthFrameDto dto = new MouthFrameDto();
            dto.setTimeMs(firstNonNullLong(
                    readLong(frameMap, "timeMs", "time_ms"),
                    readLong(frameMap, "t", "time"),
                    0L
            ));
            dto.setOpen(readDouble(frameMap, "open", "mouthOpen", "mouth_open"));
            dto.setForm(readDouble(frameMap, "form", "mouthForm", "mouth_form"));
            if (dto.getOpen() == null) {
                dto.setOpen(0D);
            }
            if (dto.getForm() == null) {
                dto.setForm(0D);
            }
            normalized.add(dto);
        }
        return normalized;
    }

    private Map<String, Object> buildAudioPayload(GuideVoiceChatResponse result) {
        if (!hasText(result.getAudioStatus())
                && !hasText(result.getAudioUrl())
                && !hasText(result.getAudioFormat())
                && !hasText(result.getTtsTaskId())
                && result.getAudioDurationMs() == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", firstNotBlank(result.getAudioStatus()));
        payload.put("taskId", firstNotBlank(result.getTtsTaskId()));
        payload.put("url", firstNotBlank(result.getAudioUrl()));
        payload.put("format", firstNotBlank(result.getAudioFormat()));
        payload.put("durationMs", result.getAudioDurationMs());
        return payload;
    }

    private void hydrateDigitalHumanPayloads(GuideVoiceChatResponse result) {
        if (result == null) {
            return;
        }
        if (!hasText(result.getTaskId())) {
            result.setTaskId(firstNotBlank(result.getTtsTaskId()));
        }
        result.setAudio(buildAudioPayload(result));
        result.setMouth(buildMouthPayload(result));
        result.setDigitalHuman(buildDigitalHumanPayload(result));
    }

    private Map<String, Object> buildMouthPayload(GuideVoiceChatResponse result) {
        if (!hasText(result.getMouthStatus())
                && (result.getMouthFrames() == null || result.getMouthFrames().isEmpty())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", firstNotBlank(result.getMouthStatus()));
        payload.put("frames", result.getMouthFrames() == null ? new ArrayList<MouthFrameDto>() : result.getMouthFrames());
        return payload;
    }

    private Map<String, Object> buildDigitalHumanPayload(GuideVoiceChatResponse result) {
        if (!hasText(result.getEmotion())
                && !hasText(result.getEmotionCode())
                && !hasText(result.getAction())
                && !hasText(result.getActionCode())
                && !hasText(result.getAvatarId())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("emotion", firstNotBlank(result.getEmotion(), result.getEmotionCode()));
        payload.put("action", firstNotBlank(result.getAction(), result.getActionCode()));
        if (hasText(result.getAvatarId())) {
            payload.put("avatarId", result.getAvatarId());
        }
        return payload;
    }

    private void logDigitalHumanDebug(Map<String, Object> audioMap,
                                      Map<String, Object> mouthMap,
                                      Map<String, Object> digitalHumanMap,
                                      Map<String, Object> responseBody,
                                      GuideVoiceChatResponse result) {
        Object rawMouthFrames = firstNonNull(
                readObject(responseBody, "mouthFrames", "mouth_frames"),
                readObject(mouthMap, "frames", "mouthFrames", "mouth_frames")
        );
        int rawFrameSize = rawMouthFrames instanceof List<?> list ? list.size() : 0;
        log.info("[DigitalHumanDebug] ai.audio=status={}, taskId={}, url={}, format={}, durationMs={}",
                readString(audioMap, "status", "audioStatus", "audio_status", "ttsStatus", "tts_status"),
                readString(audioMap, "taskId", "task_id"),
                readString(audioMap, "url", "audioUrl", "audio_url"),
                readString(audioMap, "format", "audioFormat", "audio_format"),
                readLong(audioMap, "durationMs", "duration_ms"));
        log.info("[DigitalHumanDebug] ai.mouth=status={}, frames.size={}",
                readString(mouthMap, "status", "mouthStatus", "mouth_status"),
                rawFrameSize);
        log.info("[DigitalHumanDebug] ai.digitalHuman=emotion={}, action={}",
                readString(digitalHumanMap, "emotion", "emotionCode", "emotion_code"),
                readString(digitalHumanMap, "action", "actionCode", "action_code"));
        log.info("[DigitalHumanDebug] ai.audio_url/audioUrl={}",
                firstNotBlank(
                        readString(responseBody, "audio_url", "audioUrl"),
                        readString(audioMap, "url", "audioUrl", "audio_url")
                ));
        log.info("[DigitalHumanDebug] ai.mouth_frames/mouthFrames size={}", rawFrameSize);
        log.info("[DigitalHumanDebug] app.audioStatus={}, app.audioUrl={}, app.ttsTaskId={}",
                result.getAudioStatus(), result.getAudioUrl(), result.getTtsTaskId());
        log.info("[DigitalHumanDebug] app.mouthStatus={}, app.mouthFrames.size={}",
                result.getMouthStatus(),
                result.getMouthFrames() == null ? 0 : result.getMouthFrames().size());
        log.info("[DigitalHumanDebug] app.emotion={}, app.action={}",
                firstNotBlank(result.getEmotion(), result.getEmotionCode()),
                firstNotBlank(result.getAction(), result.getActionCode()));
    }

    private String normalizeAudioUrl(String audioUrl) {
        String text = scalarToText(audioUrl);
        if (!hasText(text)) {
            return "";
        }

        if (text.startsWith("//")) {
            return "http:" + text;
        }

        if (text.startsWith("/")) {
            return trimTrailingSlash(aiProperties.getBaseUrl()) + text;
        }

        String lower = text.toLowerCase();
        if (lower.startsWith("http://127.0.0.1")
                || lower.startsWith("https://127.0.0.1")
                || lower.startsWith("http://localhost")
                || lower.startsWith("https://localhost")) {
            try {
                URI sourceUri = URI.create(text);
                URI baseUri = URI.create(trimTrailingSlash(aiProperties.getBaseUrl()));
                URI fixedUri = new URI(
                        baseUri.getScheme(),
                        baseUri.getUserInfo(),
                        baseUri.getHost(),
                        baseUri.getPort(),
                        sourceUri.getRawPath(),
                        sourceUri.getRawQuery(),
                        sourceUri.getRawFragment()
                );
                return fixedUri.toString();
            } catch (Exception e) {
                log.warn("语音音频地址本地回环改写失败，保留原地址: {}", text, e);
                return text;
            }
        }

        return text;
    }

    private String trimTrailingSlash(String value) {
        String text = scalarToText(value);
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
