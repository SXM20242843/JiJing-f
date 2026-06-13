package com.scenic.ai.modules.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.modules.app.route.dto.RouteCardDto;
import com.scenic.ai.common.config.AiProperties;
import com.scenic.ai.modules.app.route.service.RouteRecommendService;
import com.scenic.ai.modules.app.visit.service.VisitService;
import com.scenic.ai.modules.chat.dto.GuideChatRequest;
import com.scenic.ai.modules.chat.dto.GuideChatResponse;
import com.scenic.ai.modules.chat.dto.MouthFrameDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GuideChatService {

    private static final long TTS_WAIT_TIMEOUT_MS = 4000L;
    private static final long TTS_WAIT_INTERVAL_MS = 500L;
    private static final int TTS_WAIT_MAX_ATTEMPTS = 8;

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiQuestionBehaviorService aiQuestionBehaviorService;
    private final VisitService visitService;
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

        String visitId = ensureVisitSession(request);
        if (visitId != null && !visitId.isEmpty()) {
            request.setVisitId(visitId);
            visitService.bindChatSessionId(
                    parseLongOrNull(visitId),
                    request.getEffectiveUserId(),
                    request.getEffectiveSessionId()
            );
        }

        try {
            Map<String, Object> requestBody = buildAiRequestBody(request);
            RouteRecommendService.ChatRouteContext routeContext = null;
            boolean allowRoute = isExplicitRouteRequest(request);
            if (allowRoute) {
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
            }

            applyDigitalHumanOptions(requestBody);
            applyRoutePayload(requestBody, request, allowRoute);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = trimTrailingSlash(aiProperties.getBaseUrl()) + aiProperties.getTextChatEndpoint();
            log.info("调用AI文本问答服务: {}", url);
            Object candidateSpots = requestBody.get("candidate_spots");
            int candidateSpotCount = candidateSpots instanceof List<?> ? ((List<?>) candidateSpots).size() : 0;
            log.info("AI文本问答请求字段: userId={}, sessionId={}, allowRoute={}, route={}, suppressRoute={}, requestType={}, candidate_spots={}, requestKeys={}",
                    request.getEffectiveUserId(),
                    request.getEffectiveSessionId(),
                    allowRoute,
                    requestBody.get("route"),
                    requestBody.get("suppress_route"),
                    requestBody.get("request_type"),
                    candidateSpotCount,
                    requestBody.keySet());

            String currentSpotId = stringValue(requestBody.get("current_spot_id"));
            String currentSpotName = stringValue(requestBody.get("current_spot_name"));
            String visitStatus = stringValue(requestBody.get("visit_status"));
            Object isInsideAreaObj = requestBody.get("is_inside_area");
            String isInsideArea = isInsideAreaObj == null ? "null" : String.valueOf(isInsideAreaObj);
            String routeStartType = stringValue(requestBody.get("route_start_type"));
            boolean willEnhanceOnsite = allowRoute
                    && ("IN_AREA".equalsIgnoreCase(visitStatus)
                        || ("true".equalsIgnoreCase(isInsideArea) && "current_spot".equalsIgnoreCase(routeStartType)));
            log.info("[AI Route Forward] mode={}, requestType={}, routeEnabled={}, visitStatus={}, isInsideArea={}, routeStartType={}, currentSpotId={}, currentSpotName={}, candidateSpotsSize={}, willEnhanceOnsiteRoute={}",
                    request.getMode(),
                    requestBody.get("request_type"),
                    requestBody.get("route_enabled"),
                    visitStatus,
                    isInsideArea,
                    routeStartType,
                    currentSpotId,
                    currentSpotName,
                    candidateSpotCount,
                    willEnhanceOnsite);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("AI文本问答服务响应成功: status={}, responseKeys={}",
                        response.getStatusCode(), responseBody.keySet());
                GuideChatResponse result = parseAiResponse(responseBody, true);
                result = waitForTtsIfPending(result);
                result.setVisitId(visitId);

                if (allowRoute && containsRoutePayload(responseBody) && !Boolean.TRUE.equals(request.getRoute())) {
                    request.setRoute(true);
                    routeContext = routeRecommendService.enrichChatRouteRequest(
                            request,
                            request.getEffectiveUserId(),
                            new LinkedHashMap<String, Object>(requestBody)
                    );
                }
                if (allowRoute) {
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
                } else if (containsRoutePayload(responseBody)) {
                    log.info("非路线意图请求忽略 AI 返回 route，不保存路线。userId={}, sessionId={}, requestType={}",
                            request.getEffectiveUserId(),
                            request.getEffectiveSessionId(),
                            request.getRequestType());
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

    public GuideChatResponse queryTtsStatus(String taskId) {
        return queryTtsStatus(taskId, null, null);
    }

    private GuideChatResponse queryTtsStatus(String taskId, String messageId, String conversationId) {
        GuideChatResponse result = new GuideChatResponse();
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
            HttpEntity<Void> entity = new HttpEntity<Void>(headers);
            String url = buildTtsStatusUrl(taskId, messageId, conversationId);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                result = parseAiResponse(response.getBody(), false);
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

    private GuideChatResponse waitForTtsIfPending(GuideChatResponse result) {
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

            GuideChatResponse polled = queryTtsStatus(taskId, result.getMessageId(), result.getConversationId());
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

    private void mergeTtsResult(GuideChatResponse target, GuideChatResponse source) {
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

    /**
     * 仅复用请求中已有的 visitId。
     * /api/guide/chat 不负责创建 tourist_visit_session，缺少 visitId 时继续普通问答。
     */
    private String ensureVisitSession(GuideChatRequest request) {
        if (hasText(request.getVisitId())) {
            return request.getVisitId();
        }

        log.info("GuideChat 未携带 visitId，不自动创建游览会话。userId={}, mode={}, parkId={}",
                request.getEffectiveUserId(), request.getMode(), request.getEffectiveParkId());
        return null;
    }

    private Map<String, Object> buildAiRequestBody(GuideChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();

        String userId = firstNotBlank(request.getEffectiveUserId());
        String areaCode = firstNotBlank(request.getAreaCode(), request.getParkId(), "");
        String parkId = firstNotBlank(request.getParkId(), areaCode, "");
        String areaName = firstNotBlank(request.getAreaName(), request.getScenicName(), request.getParkName(), "");
        String parkName = firstNotBlank(request.getParkName(), areaName, "");
        String currentSpotId = firstNotBlank(request.getEffectiveCurrentSpotId());
        String currentSpotName = firstNotBlank(request.getEffectiveCurrentSpotName());

        body.put("user_id", userId);
        body.put("question", request.getQuestion());
        body.put("input_type", firstNotBlank(request.getInputType(), "text"));
        body.put("voice", request.getEffectiveVoice());
        body.put("enable_tts", request.getEffectiveEnableTts());
        body.put("enable_context", request.getEffectiveEnableContext());

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
        putIfHasText(body, "visit_status", request.getVisitStatus());
        if (request.getIsInsideArea() != null) {
            body.put("is_inside_area", request.getIsInsideArea());
        }
        if (request.getLatitude() != null) {
            body.put("latitude", request.getLatitude());
        }
        if (request.getLongitude() != null) {
            body.put("longitude", request.getLongitude());
        }
        if (request.getLocationContext() != null) {
            body.put("location_context", request.getLocationContext());
        }
        if (request.getNetworkContext() != null) {
            body.put("network_context", request.getNetworkContext());
        }
        if (request.getRouteIntent() != null) {
            body.put("route_intent", request.getRouteIntent());
        }
        if (request.getSuppressRoute() != null) {
            body.put("suppress_route", request.getSuppressRoute());
        }
        putIfHasText(body, "request_type", request.getRequestType());
        putIfHasText(body, "trigger", resolveTrigger(request));
        if (request.getEffectiveProfile() != null) {
            body.put("profile", request.getEffectiveProfile());
        }
        if (request.getDigitalHumanConfig() != null) {
            body.put("digital_human_config", request.getDigitalHumanConfig());
        }
        putIfHasText(body, "voice_id", request.getVoiceId());
        putIfHasText(body, "avatar_id", request.getAvatarId());

        Map<String, Object> clientContext = request.getClientContext();
        if (clientContext == null) {
            clientContext = new HashMap<String, Object>();
        }
        clientContext.put("page", clientContext.containsKey("page") ? clientContext.get("page") : "guide");
        clientContext.put("location_text", clientContext.containsKey("location_text")
                ? clientContext.get("location_text")
                : firstNotBlank(currentSpotName, areaName, request.getScenicName(), ""));
        clientContext.put("lat", clientContext.containsKey("lat") ? clientContext.get("lat") : null);
        clientContext.put("lng", clientContext.containsKey("lng") ? clientContext.get("lng") : null);
        if (request.getRouteIntent() != null) {
            clientContext.put("routeIntent", clientContext.containsKey("routeIntent")
                    ? clientContext.get("routeIntent")
                    : request.getRouteIntent());
        }
        if (request.getSuppressRoute() != null) {
            clientContext.put("suppressRoute", clientContext.containsKey("suppressRoute")
                    ? clientContext.get("suppressRoute")
                    : request.getSuppressRoute());
        }
        if (hasText(request.getVisitStatus()) && !clientContext.containsKey("visit_status")) {
            clientContext.put("visit_status", request.getVisitStatus());
        }
        if (request.getIsInsideArea() != null && !clientContext.containsKey("is_inside_area")) {
            clientContext.put("is_inside_area", request.getIsInsideArea());
        }
        if (hasText(request.getRequestType()) && !clientContext.containsKey("requestType")) {
            clientContext.put("requestType", request.getRequestType());
        }
        body.put("client_context", clientContext);

        return body;
    }

    private void applyDigitalHumanOptions(Map<String, Object> body) {
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        options.put("responseMode", "digital_human");
        options.put("enableTts", true);
        options.put("ttsMode", "async");
        options.put("includeMouthFrames", true);
        options.put("includeSources", false);
        options.put("includeDebug", false);
        body.put("options", options);
    }

    private void applyRoutePayload(Map<String, Object> body, GuideChatRequest request, boolean allowRoute) {
        Map<String, Object> routePayload = new LinkedHashMap<String, Object>();
        routePayload.put("enabled", allowRoute);

        if (allowRoute) {
            Integer availableMinutes = request.getAvailableMinutes();
            if (availableMinutes == null) {
                availableMinutes = readInteger(body.get("available_minutes"));
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

    private String resolveTrigger(GuideChatRequest request) {
        String requestType = firstNotBlank(request.getRequestType()).toLowerCase();
        if ("spot_explain".equals(requestType)) {
            return "ARRIVE_SPOT";
        }

        Map<String, Object> clientContext = request.getClientContext();
        if (clientContext != null) {
            String contextRequestType = firstNotBlank(
                    stringValue(clientContext.get("requestType")),
                    stringValue(clientContext.get("request_type"))
            ).toLowerCase();
            if ("spot_explain".equals(contextRequestType)) {
                return "ARRIVE_SPOT";
            }
        }

        return "";
    }

    private boolean isExplicitRouteRequest(GuideChatRequest request) {
        if (request == null) {
            return false;
        }
        if (Boolean.TRUE.equals(request.getSuppressRoute())) {
            return false;
        }

        String requestType = firstNotBlank(request.getRequestType()).toLowerCase();
        if ("spot_explain".equals(requestType) || "welcome".equals(requestType)) {
            return false;
        }

        Map<String, Object> clientContext = request.getClientContext();
        if (clientContext != null) {
            if (isTruthy(clientContext.get("suppressRoute")) || isTruthy(clientContext.get("suppress_route"))) {
                return false;
            }
            String contextRequestType = firstNotBlank(
                    stringValue(clientContext.get("requestType")),
                    stringValue(clientContext.get("request_type"))
            ).toLowerCase();
            if ("spot_explain".equals(contextRequestType) || "welcome".equals(contextRequestType)) {
                return false;
            }
            String routeTrigger = firstNotBlank(
                    stringValue(clientContext.get("routeTrigger")),
                    stringValue(clientContext.get("route_trigger"))
            );
            if ("manual".equalsIgnoreCase(routeTrigger)) {
                return true;
            }
            if (isTruthy(clientContext.get("routeIntent")) || isTruthy(clientContext.get("route_intent"))) {
                return true;
            }
        }
        if (Boolean.TRUE.equals(request.getRoute()) || Boolean.TRUE.equals(request.getRouteIntent())) {
            return true;
        }

        return hasRouteIntentText(firstNotBlank(request.getQuestion()));
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

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = stringValue(value).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text) || "y".equals(text) || "是".equals(text);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private GuideChatResponse parseAiResponse(Map<String, Object> responseBody, boolean fillDefaultAnswer) {
        Map<String, Object> dataMap = responseBody;
        Object dataObj = responseBody.get("data");
        if (dataObj instanceof Map<?, ?>) {
            dataMap = (Map<String, Object>) dataObj;
        }

        Map<String, Object> audioMap = readMapCascade(dataMap, responseBody,
                "audio", "tts", "voice", "speech", "audioData", "audio_data");
        Map<String, Object> mouthMap = readMapCascade(dataMap, responseBody,
                "mouth", "mouthSync", "mouth_sync", "lipSync", "lip_sync", "mouthData", "mouth_data");
        Map<String, Object> digitalHumanMap = readMapCascade(dataMap, responseBody, "digitalHuman", "digital_human");

        GuideChatResponse result = new GuideChatResponse();
        String recognizedText = readString(dataMap,
                "recognized_text", "recognizedText", "asr_text", "asrText", "questionText", "question_text");
        result.setRecognizedText(recognizedText);
        result.setQuestionText(firstNotBlank(recognizedText, readString(dataMap, "question")));
        result.setConversationId(firstNotBlank(
                readString(dataMap, "conversation_id", "conversationId"),
                readString(responseBody, "conversation_id", "conversationId")
        ));
        result.setMessageId(firstNotBlank(
                readString(dataMap, "message_id", "messageId"),
                readString(responseBody, "message_id", "messageId")
        ));
        result.setAnswer(firstNotBlank(
                readString(dataMap, "answer", "content"),
                readString(responseBody, "answer", "content")
        ));
        result.setRewrittenQuestion(firstNotBlank(
                readString(dataMap, "rewritten_question", "rewrittenQuestion"),
                readString(responseBody, "rewritten_question", "rewrittenQuestion")
        ));
        result.setIntent(firstNotBlank(
                readString(dataMap, "intent"),
                readString(responseBody, "intent")
        ));
        result.setAudioUrl(normalizeAudioUrl(firstNotBlank(
                readString(dataMap, "audio_url", "audioUrl"),
                readString(responseBody, "audio_url", "audioUrl"),
                readString(audioMap, "url", "audioUrl", "audio_url")
        )));
        result.setAudioFormat(firstNotBlank(
                readString(dataMap, "audio_format", "audioFormat"),
                readString(responseBody, "audio_format", "audioFormat"),
                readString(audioMap, "format", "audioFormat", "audio_format")
        ));
        result.setAudioStatus(firstNotBlank(
                readString(dataMap, "audio_status", "audioStatus", "tts_status", "ttsStatus"),
                readString(responseBody, "audio_status", "audioStatus", "tts_status", "ttsStatus"),
                readString(audioMap, "status", "audioStatus", "audio_status", "ttsStatus", "tts_status")
        ));
        result.setTtsTaskId(firstNotBlank(
                readString(dataMap, "ttsTaskId", "tts_task_id", "taskId", "task_id"),
                readString(responseBody, "ttsTaskId", "tts_task_id", "taskId", "task_id"),
                readString(audioMap, "taskId", "task_id")
        ));
        result.setTaskId(firstNotBlank(result.getTtsTaskId()));
        result.setAudioDurationMs(firstNonNullLong(
                readLong(dataMap, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms"),
                readLong(responseBody, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms"),
                readLong(audioMap, "durationMs", "duration_ms")
        ));
        result.setTtsError(firstNotBlank(
                readString(dataMap, "tts_error", "ttsError"),
                readString(responseBody, "tts_error", "ttsError"),
                readString(audioMap, "error", "ttsError", "tts_error")
        ));

        Object currentEntity = firstNonNull(
                readObject(dataMap, "current_entity", "currentEntity"),
                readObject(responseBody, "current_entity", "currentEntity")
        );
        if (currentEntity instanceof Map<?, ?>) {
            result.setCurrentEntity(objectMapper.convertValue(currentEntity, new TypeReference<Map<String, Object>>() {
            }));
        }
        Object suggestions = firstNonNull(
                readObject(dataMap, "suggestions"),
                readObject(responseBody, "suggestions")
        );
        if (suggestions instanceof List<?>) {
            result.setSuggestions(objectMapper.convertValue(suggestions, new TypeReference<List<String>>() {
            }));
        }
        Object sources = firstNonNull(
                readObject(dataMap, "sources"),
                readObject(responseBody, "sources")
        );
        if (sources instanceof List<?>) {
            result.setSources(objectMapper.convertValue(sources, new TypeReference<List<Map<String, Object>>>() {
            }));
        }

        Object mouthFrames = firstNonNull(
                readObject(dataMap, "mouthFrames", "mouth_frames"),
                readObject(responseBody, "mouthFrames", "mouth_frames"),
                readNestedObject(dataMap, "mouth", "frames", "mouthFrames", "mouth_frames"),
                readNestedObject(responseBody, "mouth", "frames", "mouthFrames", "mouth_frames"),
                readObject(mouthMap, "frames", "mouthFrames", "mouth_frames"),
                readObject(audioMap, "mouthFrames", "mouth_frames", "frames")
        );
        if (mouthFrames instanceof List<?>) {
            result.setMouthFrames(normalizeMouthFrames((List<?>) mouthFrames));
        }
        result.setMouthStatus(firstNotBlank(
                readString(dataMap, "mouthStatus", "mouth_status"),
                readString(responseBody, "mouthStatus", "mouth_status"),
                readString(mouthMap, "status", "mouthStatus", "mouth_status")
        ));
        result.setEmotion(firstNotBlank(
                readString(dataMap, "emotion", "emotion_code", "emotionCode"),
                readString(responseBody, "emotion", "emotion_code", "emotionCode"),
                readString(digitalHumanMap, "emotion")
        ));
        result.setEmotionCode(firstNotBlank(
                readString(dataMap, "emotion_code", "emotionCode"),
                readString(responseBody, "emotion_code", "emotionCode"),
                readString(digitalHumanMap, "emotionCode", "emotion_code")
        ));
        result.setAction(firstNotBlank(
                readString(dataMap, "action", "action_code", "actionCode"),
                readString(responseBody, "action", "action_code", "actionCode"),
                readString(digitalHumanMap, "action")
        ));
        result.setActionCode(firstNotBlank(
                readString(dataMap, "action_code", "actionCode"),
                readString(responseBody, "action_code", "actionCode"),
                readString(digitalHumanMap, "actionCode", "action_code")
        ));
        result.setAvatarId(firstNotBlank(
                readString(dataMap, "avatar_id", "avatarId"),
                readString(responseBody, "avatar_id", "avatarId"),
                readString(digitalHumanMap, "avatarId", "avatar_id")
        ));
        result.setRoute(readRawRouteCard(dataMap, responseBody));
        Object routeRecommendation = firstNonNull(
                readObject(dataMap, "route_recommendation", "routeRecommendation"),
                readObject(responseBody, "route_recommendation", "routeRecommendation")
        );
        result.setRouteRecommendation(routeRecommendation);
        result.setRouteRecommendationSnake(routeRecommendation);

        result.setAudio(buildAudioPayload(result));
        result.setMouth(buildMouthPayload(result));
        result.setDigitalHuman(buildDigitalHumanPayload(result));
        hydrateDigitalHumanPayloads(result);

        if (fillDefaultAnswer && !hasText(result.getAnswer())) {
            result.setAnswer("已收到你的问题，后续这里将展示后端返回的真实答案。");
        }

        logDigitalHumanDebug(audioMap, mouthMap, digitalHumanMap, responseBody, result);
        return result;
    }

    private RouteCardDto readRawRouteCard(Map<String, Object> dataMap, Map<String, Object> responseBody) {
        Object routeObj = firstNonNull(
                readObject(dataMap, "route"),
                readObject(responseBody, "route")
        );
        if (routeObj instanceof Map<?, ?>) {
            return objectMapper.convertValue(routeObj, RouteCardDto.class);
        }
        return null;
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
        if (dataObj instanceof Map<?, ?>) {
            return hasRouteKey((Map<String, Object>) dataObj);
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
        if (hasText(value)) {
            body.put(key, value);
        }
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
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Integer readInteger(Object value) {
        Long longValue = readLongValue(value);
        return longValue == null ? null : longValue.intValue();
    }

    private Object readObject(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMapCascade(Map<String, Object> primary,
                                               Map<String, Object> fallback,
                                               String... keys) {
        Object object = firstNonNull(readObject(primary, keys), readObject(fallback, keys));
        if (object instanceof Map<?, ?>) {
            return (Map<String, Object>) object;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object readNestedObject(Map<String, Object> map, String objectKey, String... keys) {
        Object nested = readObject(map, objectKey);
        if (nested instanceof Map<?, ?>) {
            return readObject((Map<String, Object>) nested, keys);
        }
        return null;
    }

    private String readString(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        return scalarToText(value);
    }

    private Long readLong(Map<String, Object> map, String... keys) {
        return readLongValue(readObject(map, keys));
    }

    private Long readLongValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
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

    private Double readDouble(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
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
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
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

    private List<MouthFrameDto> normalizeMouthFrames(List<?> frames) {
        List<MouthFrameDto> normalized = new ArrayList<MouthFrameDto>();
        if (frames == null || frames.isEmpty()) {
            return normalized;
        }
        for (Object frame : frames) {
            if (!(frame instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> frameMap = (Map<String, Object>) frame;
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

    private Map<String, Object> buildAudioPayload(GuideChatResponse result) {
        if (!hasText(result.getAudioStatus())
                && !hasText(result.getAudioUrl())
                && !hasText(result.getAudioFormat())
                && !hasText(result.getTtsTaskId())
                && result.getAudioDurationMs() == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("status", firstNotBlank(result.getAudioStatus()));
        payload.put("taskId", firstNotBlank(result.getTtsTaskId()));
        payload.put("url", firstNotBlank(result.getAudioUrl()));
        payload.put("format", firstNotBlank(result.getAudioFormat()));
        payload.put("durationMs", result.getAudioDurationMs());
        return payload;
    }

    private Map<String, Object> buildMouthPayload(GuideChatResponse result) {
        if (!hasText(result.getMouthStatus())
                && (result.getMouthFrames() == null || result.getMouthFrames().isEmpty())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("status", firstNotBlank(result.getMouthStatus()));
        payload.put("frames", result.getMouthFrames() == null ? new ArrayList<MouthFrameDto>() : result.getMouthFrames());
        return payload;
    }

    private void hydrateDigitalHumanPayloads(GuideChatResponse result) {
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

    private Map<String, Object> buildDigitalHumanPayload(GuideChatResponse result) {
        if (!hasText(result.getEmotion())
                && !hasText(result.getEmotionCode())
                && !hasText(result.getAction())
                && !hasText(result.getActionCode())
                && !hasText(result.getAvatarId())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
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
                                      GuideChatResponse result) {
        Object rawMouthFrames = firstNonNull(
                readObject(responseBody, "mouthFrames", "mouth_frames"),
                readObject(mouthMap, "frames", "mouthFrames", "mouth_frames")
        );
        int rawFrameSize = rawMouthFrames instanceof List<?> ? ((List<?>) rawMouthFrames).size() : 0;
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

    private String trimTrailingSlash(String value) {
        String text = firstNotBlank(value);
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
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
                log.warn("文本音频地址本地回环改写失败，保留原地址: {}", text, e);
                return text;
            }
        }

        return text;
    }
}
