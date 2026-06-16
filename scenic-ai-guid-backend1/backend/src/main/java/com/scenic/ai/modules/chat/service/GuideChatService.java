package com.scenic.ai.modules.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.modules.app.route.dto.RouteCardDto;
import com.scenic.ai.common.config.AiProperties;
import com.scenic.ai.modules.app.route.client.ProfileContextClient;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class GuideChatService {

    private static final long TTS_WAIT_TIMEOUT_MS = 4000L;
    private static final long TTS_WAIT_INTERVAL_MS = 500L;
    private static final int TTS_WAIT_MAX_ATTEMPTS = 8;
    private static final Set<String> FINAL_AI_PAYLOAD_KEEP_KEYS = new HashSet<String>(Arrays.asList(
            "question",
            "user_id",
            "session_id",
            "area_id",
            "area_code",
            "area_name",
            "visit_id",
            "visit_status",
            "current_spot_id",
            "current_spot_name",
            "current_location",
            "location_context",
            "route",
            "user_profile",
            "short_term_context",
            "available_minutes",
            "candidate_spots",
            "enable_personalization",
            "options",
            "input_type",
            "enable_tts",
            "enable_context",
            "voice",
            "voice_id",
            "avatar_id",
            "digital_human_config"
    ));

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiQuestionBehaviorService aiQuestionBehaviorService;
    private final VisitService visitService;
    private final ChatPersistenceService chatPersistenceService;
    private final RouteRecommendService routeRecommendService;
    private final ProfileContextClient profileContextClient;

    public GuideChatService(RestTemplate restTemplate,
                            AiProperties aiProperties,
                            ObjectMapper objectMapper,
                            AiQuestionBehaviorService aiQuestionBehaviorService,
                            VisitService visitService,
                            ChatPersistenceService chatPersistenceService,
                            RouteRecommendService routeRecommendService,
                            ProfileContextClient profileContextClient) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.aiQuestionBehaviorService = aiQuestionBehaviorService;
        this.visitService = visitService;
        this.chatPersistenceService = chatPersistenceService;
        this.routeRecommendService = routeRecommendService;
        this.profileContextClient = profileContextClient;
    }

    public GuideChatResponse callAiService(GuideChatRequest request) {
        chatPersistenceService.ensureTextSessionId(request);

        GuideVisitContextResolver.VisitContext initialVisitContext = prepareVisitContext(request);
        String visitId = initialVisitContext.isInPark() ? ensureVisitSession(request) : null;
        if (!initialVisitContext.isInPark()) {
            request.setVisitId(null);
        }
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

            applyDigitalHumanOptions(requestBody, request.getOptions());
            applyRoutePayload(requestBody, request, allowRoute);
            applyUserProfileSnapshot(requestBody, request);
            finalizeAiRequestBody(requestBody, request, allowRoute);
            logRouteContextPayload(requestBody, allowRoute);
            logRouteContractRequest(requestBody, allowRoute);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = trimTrailingSlash(aiProperties.getBaseUrl()) + aiProperties.getTextChatEndpoint();
            log.info("[GuideChat] request url={}", url);
            log.info("[GuideChat] request body={}", sanitizeBodyForLog(requestBody));
            Object candidateSpots = requestBody.get("candidate_spots");
            int candidateSpotCount = candidateSpots instanceof List<?> ? ((List<?>) candidateSpots).size() : 0;
            log.info("[GuideChat] request fields: userId={}, sessionId={}, conversationId={}, areaCode={}, areaId={}, currentSpotId={}, currentSpotName={}, sceneCode={}, allowRoute={}, route={}, suppressRoute={}, requestType={}, candidate_spots={}, requestKeys={}",
                    request.getEffectiveUserId(),
                    request.getEffectiveSessionId(),
                    request.getEffectiveConversationId(),
                    requestBody.get("area_code"),
                    requestBody.get("area_id"),
                    requestBody.get("current_spot_id"),
                    requestBody.get("current_spot_name"),
                    requestBody.get("scene_code"),
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
                    && ("IN_PARK".equalsIgnoreCase(normalizeVisitStatus(visitStatus))
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
            log.info("[GuideChat] httpStatus={}", response.getStatusCode());
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("[GuideChat] responseBody={}", sanitizeBodyForLog(responseBody));
                logProfileDeltaIgnored(responseBody, request.getEffectiveUserId(), request.getEffectiveSessionId());
                GuideChatResponse result = parseAiResponse(responseBody, true);
                result = waitForTtsIfPending(result);
                result.setVisitId(visitId);
                log.info("[GuideChat] parsed answerStatus={}, interactionCategory={}, conversationId={}, success=true",
                        result.getAnswerStatus(),
                        result.getInteractionCategory(),
                        result.getConversationId());

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
                logRouteContractResponse(result);

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
        } catch (HttpStatusCodeException e) {
            log.warn("[GuideChat] AI request failed, status={}, body={}",
                    e.getStatusCode().value(),
                    truncateAiErrorBody(e.getResponseBodyAsString()));
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

    public StreamingResponseBody streamAiService(GuideChatRequest request) {
        return outputStream -> streamAiServiceToOutput(request, outputStream);
    }

    private void streamAiServiceToOutput(GuideChatRequest request, OutputStream outputStream) throws IOException {
        chatPersistenceService.ensureTextSessionId(request);

        GuideVisitContextResolver.VisitContext initialVisitContext = prepareVisitContext(request);
        String visitId = initialVisitContext.isInPark() ? ensureVisitSession(request) : null;
        if (!initialVisitContext.isInPark()) {
            request.setVisitId(null);
        }
        if (visitId != null && !visitId.isEmpty()) {
            request.setVisitId(visitId);
            visitService.bindChatSessionId(
                    parseLongOrNull(visitId),
                    request.getEffectiveUserId(),
                    request.getEffectiveSessionId()
            );
        }

        HttpURLConnection connection = null;
        boolean aiResponseStarted = false;
        try {
            Map<String, Object> requestBody = buildAiRequestBody(request);
            boolean allowRoute = isExplicitRouteRequest(request);
            if (allowRoute) {
                try {
                    routeRecommendService.enrichChatRouteRequest(
                            request,
                            request.getEffectiveUserId(),
                            requestBody
                    );
                } catch (Exception e) {
                    log.warn("SSE Chat 路线请求增强失败，继续普通流式问答。userId={}, sessionId={}",
                            request.getEffectiveUserId(), request.getEffectiveSessionId(), e);
                }
            }

            applyDigitalHumanOptions(requestBody, request.getOptions());
            applyRoutePayload(requestBody, request, allowRoute);
            applyUserProfileSnapshot(requestBody, request);
            finalizeAiRequestBody(requestBody, request, allowRoute);
            logRouteContextPayload(requestBody, allowRoute);
            logRouteContractRequest(requestBody, allowRoute);

            String url = buildTextChatUrl();
            byte[] payload = objectMapper.writeValueAsBytes(requestBody);
            log.info("[GuideChatSse] request url={}", url);
            log.info("[GuideChatSse] request body={}", sanitizeBodyForLog(requestBody));

            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(0);

            try (OutputStream aiOutput = connection.getOutputStream()) {
                aiOutput.write(payload);
                aiOutput.flush();
            }

            int statusCode = connection.getResponseCode();
            log.info("[GuideChatSse] httpStatus={}", statusCode);

            InputStream aiInput = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (aiInput == null) {
                writeSseError(outputStream, statusCode, "AI服务暂时不可用，请稍后再试。");
                return;
            }

            if (statusCode >= 400) {
                String errorBody = readSmallErrorBody(aiInput);
                log.warn("[GuideChatSse] AI request failed, status={}, body={}",
                        statusCode,
                        truncateAiErrorBody(errorBody));
                writeSseError(outputStream, statusCode, errorBody);
                return;
            }

            try (InputStream input = aiInput) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    aiResponseStarted = true;
                    outputStream.write(buffer, 0, read);
                    outputStream.flush();
                }
            }
        } catch (IOException e) {
            if (aiResponseStarted) {
                log.info("[GuideChatSse] 客户端断开或 AI 流中断，已关闭 AI 连接。userId={}, sessionId={}, message={}",
                        request.getEffectiveUserId(), request.getEffectiveSessionId(), e.getMessage());
            } else {
                log.warn("[GuideChatSse] 建立 AI 流式连接失败。userId={}, sessionId={}, message={}",
                        request.getEffectiveUserId(), request.getEffectiveSessionId(), e.getMessage());
                writeSseErrorQuietly(outputStream, 500, "抱歉，AI服务暂时不可用，请稍后再试。");
            }
        } catch (Exception e) {
            log.error("[GuideChatSse] 调用 AI 流式问答服务失败", e);
            writeSseErrorQuietly(outputStream, 500, "抱歉，AI服务暂时不可用，请稍后再试。");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
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

    private String buildTextChatUrl() {
        String endpoint = firstNotBlank(aiProperties.getTextChatEndpoint(), "/api/chat");
        String lowerEndpoint = endpoint.toLowerCase();
        if (lowerEndpoint.startsWith("http://") || lowerEndpoint.startsWith("https://")) {
            return endpoint;
        }
        return trimTrailingSlash(aiProperties.getBaseUrl()) + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
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

    private void writeSseError(OutputStream outputStream, int code, String message) throws IOException {
        String payload = "event: error\n"
                + "data: {\"code\":" + code + ",\"message\":\"" + escapeJson(message) + "\"}\n\n";
        outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void writeSseErrorQuietly(OutputStream outputStream, int code, String message) {
        try {
            writeSseError(outputStream, code, message);
        } catch (IOException ignored) {
            // 客户端已断开时无需再写错误事件。
        }
    }

    private String readSmallErrorBody(InputStream errorStream) throws IOException {
        if (errorStream == null) {
            return "AI服务暂时不可用，请稍后再试。";
        }
        try (InputStream input = errorStream) {
            String body = new String(input.readNBytes(4096), StandardCharsets.UTF_8).trim();
            return hasText(body) ? body : "AI服务暂时不可用，请稍后再试。";
        }
    }

    private String truncateAiErrorBody(String body) {
        String text = body == null ? "" : body.trim();
        if (text.length() <= 2000) {
            return text;
        }
        return text.substring(0, 2000);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
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
        String normalizedVisitStatus = normalizeVisitStatus(request.getVisitStatus());
        boolean insideArea = request.getIsInsideArea() != null
                ? request.getIsInsideArea()
                : "IN_PARK".equals(normalizedVisitStatus);

        body.put("userId", userId);
        body.put("user_id", userId);
        body.put("question", firstNotBlank(request.getQuestion(), request.getRawQuestion()));
        putIfHasText(body, "rawQuestion", request.getRawQuestion());
        putIfHasText(body, "raw_question", request.getRawQuestion());
        body.put("inputType", firstNotBlank(request.getInputType(), "text"));
        body.put("input_type", firstNotBlank(request.getInputType(), "text"));
        body.put("voice", request.getEffectiveVoice());
        body.put("enableTts", request.getEffectiveEnableTts());
        body.put("enable_tts", request.getEffectiveEnableTts());
        body.put("ttsEnabled", request.getEffectiveEnableTts());
        body.put("tts_enabled", request.getEffectiveEnableTts());
        body.put("enableContext", request.getEffectiveEnableContext());
        body.put("enable_context", request.getEffectiveEnableContext());

        putIfHasText(body, "sessionId", request.getEffectiveSessionId());
        putIfHasText(body, "session_id", request.getEffectiveSessionId());
        putIfHasText(body, "conversationId", request.getEffectiveConversationId());
        putIfHasText(body, "conversation_id", request.getEffectiveConversationId());
        putIfHasText(body, "visitId", request.getVisitId());
        putIfHasText(body, "visit_id", request.getVisitId());
        putIfHasText(body, "visitNo", request.getVisitNo());
        putIfHasText(body, "visit_no", request.getVisitNo());
        putIfHasText(body, "mode", request.getMode());
        if (request.getAreaId() != null) {
            body.put("areaId", request.getAreaId());
            body.put("area_id", request.getAreaId());
        }
        putIfHasText(body, "parkId", parkId);
        putIfHasText(body, "park_id", parkId);
        putIfHasText(body, "parkName", parkName);
        putIfHasText(body, "park_name", parkName);
        putIfHasText(body, "scenicId", currentSpotId);
        putIfHasText(body, "scenic_id", currentSpotId);
        putIfHasText(body, "scenicName", currentSpotName);
        putIfHasText(body, "scenic_name", currentSpotName);
        putIfHasText(body, "areaCode", areaCode);
        putIfHasText(body, "area_code", areaCode);
        putIfHasText(body, "areaName", areaName);
        putIfHasText(body, "area_name", areaName);
        putIfHasText(body, "currentSpotId", currentSpotId);
        putIfHasText(body, "current_spot_id", currentSpotId);
        putIfHasText(body, "currentSpotName", currentSpotName);
        putIfHasText(body, "current_spot_name", currentSpotName);
        putIfHasText(body, "sceneCode", request.getSceneCode());
        putIfHasText(body, "scene_code", request.getSceneCode());
        body.put("visitStatus", normalizedVisitStatus);
        body.put("visit_status", normalizedVisitStatus);
        putIfHasText(body, "routeStartType", request.getRouteStartType());
        putIfHasText(body, "route_start_type", request.getRouteStartType());
        body.put("isInsideArea", insideArea);
        body.put("is_inside_area", insideArea);
        if (request.getAvailableMinutes() != null) {
            body.put("availableMinutes", request.getAvailableMinutes());
            body.put("available_minutes", request.getAvailableMinutes());
        }
        Integer remainingMinutes = request.getRemainingMinutes() == null
                ? request.getAvailableMinutes()
                : request.getRemainingMinutes();
        if (remainingMinutes != null) {
            body.put("remainingMinutes", remainingMinutes);
            body.put("remaining_minutes", remainingMinutes);
        }
        Object travelParty = normalizeFlexibleObject(request.getTravelParty());
        if (travelParty == null) {
            travelParty = buildTravelParty(request.getGroupSize(), request.getTravelType(), request.getVisitPreference());
        }
        if (travelParty != null) {
            body.put("travelParty", travelParty);
            body.put("travel_party", travelParty);
        }
        if (request.getLatitude() != null) {
            body.put("latitude", request.getLatitude());
        }
        if (request.getLongitude() != null) {
            body.put("longitude", request.getLongitude());
        }
        Map<String, Object> location = buildRouteContractLocation(request);
        if (!location.isEmpty()) {
            body.put("location", location);
        }
        Map<String, Object> currentSpot = buildRouteContractCurrentSpot(request, currentSpotId, currentSpotName);
        if (!currentSpot.isEmpty()) {
            body.put("currentSpot", currentSpot);
            body.put("current_spot", currentSpot);
        }
        Object context = normalizeFlexibleObject(request.getContext());
        if (context != null) {
            body.put("context", context);
        }
        Object locationContext = normalizeFlexibleObject(request.getLocationContext());
        if (locationContext != null) {
            body.put("locationContext", locationContext);
            body.put("location_context", locationContext);
        }
        Object networkContext = normalizeFlexibleObject(request.getNetworkContext());
        if (networkContext != null) {
            body.put("networkContext", networkContext);
            body.put("network_context", networkContext);
        }
        if (request.getRouteIntent() != null) {
            body.put("routeIntent", request.getRouteIntent());
            body.put("route_intent", request.getRouteIntent());
        }
        if (request.getSuppressRoute() != null) {
            body.put("suppressRoute", request.getSuppressRoute());
            body.put("suppress_route", request.getSuppressRoute());
        }
        putIfHasText(body, "requestType", request.getRequestType());
        putIfHasText(body, "request_type", request.getRequestType());
        Object shortTermContext = normalizeFlexibleObject(request.getShortTermContext());
        if (shortTermContext != null) {
            body.put("shortTermContext", shortTermContext);
            body.put("short_term_context", shortTermContext);
        }
        putIfHasText(body, "trigger", resolveTrigger(request));
        if (request.getEffectiveProfile() != null) {
            body.put("profile", request.getEffectiveProfile());
        }
        Object profileSnapshot = normalizeFlexibleObject(request.getProfileSnapshot());
        if (profileSnapshot != null) {
            body.put("profileSnapshot", profileSnapshot);
            body.put("profile_snapshot", profileSnapshot);
        }
        Object currentVisitContext = normalizeFlexibleObject(request.getCurrentVisitContext());
        if (currentVisitContext != null) {
            body.put("currentVisitContext", currentVisitContext);
            body.put("current_visit_context", currentVisitContext);
        }
        if (request.getDigitalHumanConfig() != null) {
            body.put("digitalHumanConfig", request.getDigitalHumanConfig());
            body.put("digital_human_config", request.getDigitalHumanConfig());
        }
        putIfHasText(body, "voiceId", request.getVoiceId());
        putIfHasText(body, "voice_id", request.getVoiceId());
        putIfHasText(body, "avatarId", request.getAvatarId());
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
        if (!clientContext.containsKey("visit_status")) {
            clientContext.put("visit_status", normalizedVisitStatus);
        }
        if (!clientContext.containsKey("visitStatus")) {
            clientContext.put("visitStatus", normalizedVisitStatus);
        }
        if (!clientContext.containsKey("is_inside_area")) {
            clientContext.put("is_inside_area", insideArea);
        }
        if (!clientContext.containsKey("isInsideArea")) {
            clientContext.put("isInsideArea", insideArea);
        }
        if (hasText(request.getRequestType()) && !clientContext.containsKey("requestType")) {
            clientContext.put("requestType", request.getRequestType());
        }
        body.put("client_context", clientContext);

        return body;
    }

    private void applyDigitalHumanOptions(Map<String, Object> body, Object requestOptions) {
        Map<String, Object> options = asMutableMap(normalizeFlexibleObject(requestOptions));
        if (options == null) {
            options = new LinkedHashMap<String, Object>();
        }
        options.putIfAbsent("responseMode", "digital_human");
        options.putIfAbsent("enableTts", true);
        options.putIfAbsent("ttsMode", "async");
        options.putIfAbsent("includeMouthFrames", true);
        options.putIfAbsent("includeSources", false);
        options.putIfAbsent("includeDebug", false);
        body.put("options", options);
    }

    private void applyRoutePayload(Map<String, Object> body, GuideChatRequest request, boolean allowRoute) {
        Map<String, Object> routePayload = asMutableMap(normalizeFlexibleObject(request.getRoutePayload()));
        if (routePayload == null) {
            routePayload = new LinkedHashMap<String, Object>();
        }
        routePayload.put("enabled", allowRoute);

        if (allowRoute) {
            Integer availableMinutes = request.getAvailableMinutes();
            if (availableMinutes == null) {
                availableMinutes = readInteger(body.get("available_minutes"));
            }
            if (availableMinutes != null) {
                routePayload.put("available_minutes", availableMinutes);
            }
            routePayload.put("preference_tags",
                    request.getPreferenceTags() == null ? new ArrayList<String>() : request.getPreferenceTags());
            routePayload.put("avoid_tags", new ArrayList<String>());
            routePayload.remove("availableMinutes");
            routePayload.remove("preferenceTags");
            routePayload.remove("avoidTags");
            body.put("route", routePayload);
            return;
        }

        body.put("route", routePayload);
    }

    private GuideVisitContextResolver.VisitContext prepareVisitContext(GuideChatRequest request) {
        GuideVisitContextResolver.VisitContext context = GuideVisitContextResolver.resolveVisitContext(request);
        if (request != null) {
            request.setVisitStatus(context.getVisitStatus());
            request.setIsInsideArea(context.isInPark());
        }
        return context;
    }

    private void finalizeAiRequestBody(Map<String, Object> body, GuideChatRequest request, boolean allowRoute) {
        if (body == null || request == null) {
            return;
        }

        GuideVisitContextResolver.VisitContext visitContext = prepareVisitContext(request);
        body.put("visit_status", visitContext.getVisitStatus());
        putIfHasText(body, "user_id", request.getEffectiveUserId());
        putIfHasText(body, "session_id", request.getEffectiveSessionId());
        if (request.getAreaId() != null) {
            body.put("area_id", request.getAreaId());
        }
        putIfHasText(body, "area_code", firstNotBlank(request.getAreaCode(), request.getParkId()));
        putIfHasText(body, "area_name", firstNotBlank(request.getAreaName(), request.getScenicName(), request.getParkName()));

        normalizeRoutePayloadForAi(body, request, allowRoute);
        normalizeProfilePayloadForAi(body);
        normalizeOperationalOptionsForAi(body);

        if (visitContext.isInPark()) {
            putIfHasText(body, "visit_id", request.getVisitId());
            String currentSpotId = firstNotBlank(visitContext.getCurrentSpotId(), stringValue(body.get("current_spot_id")));
            String currentSpotName = firstNotBlank(visitContext.getCurrentSpotName(), stringValue(body.get("current_spot_name")));
            if (GuideVisitContextResolver.isUsableCurrentSpot(
                    currentSpotId,
                    currentSpotName,
                    request.getAreaId(),
                    firstNotBlank(request.getAreaCode(), request.getParkId()),
                    firstNotBlank(request.getAreaName(), request.getParkName()),
                    request.getParkId(),
                    request.getParkName())) {
                putIfHasText(body, "current_spot_id", currentSpotId);
                putIfHasText(body, "current_spot_name", currentSpotName);
            } else {
                body.remove("current_spot_id");
                body.remove("current_spot_name");
            }

            Map<String, Object> currentLocation = buildCurrentLocationForAi(body, request);
            if (!currentLocation.isEmpty()) {
                body.put("current_location", currentLocation);
            } else {
                body.remove("current_location");
            }

            Map<String, Object> locationContext = sanitizeLocationContextForAi(
                    firstNonNull(body.get("location_context"), request.getLocationContext())
            );
            if (!locationContext.isEmpty()) {
                body.put("location_context", locationContext);
            } else if (!currentLocation.isEmpty()) {
                Map<String, Object> fallbackContext = new LinkedHashMap<String, Object>();
                fallbackContext.put("source", "SIMULATED");
                fallbackContext.put("confidence_level", "HIGH");
                body.put("location_context", fallbackContext);
            } else {
                body.remove("location_context");
            }
        } else {
            body.remove("visit_id");
            body.remove("current_spot_id");
            body.remove("current_spot_name");
            body.remove("current_location");
            body.remove("location_context");
            body.remove("candidate_spots");
            body.remove("short_term_context");
        }

        removeDisallowedAiPayloadFields(body);
    }

    private void normalizeRoutePayloadForAi(Map<String, Object> body, GuideChatRequest request, boolean allowRoute) {
        Map<String, Object> routePayload = asMutableMap(normalizeFlexibleObject(body.get("route")));
        if (routePayload == null) {
            routePayload = new LinkedHashMap<String, Object>();
        }
        routePayload.put("enabled", allowRoute);
        Integer availableMinutes = request.getAvailableMinutes();
        if (availableMinutes == null) {
            availableMinutes = readInteger(firstNonNull(routePayload.get("available_minutes"), body.get("available_minutes")));
        }
        if (availableMinutes != null) {
            routePayload.put("available_minutes", availableMinutes);
            body.put("available_minutes", availableMinutes);
        }
        routePayload.remove("availableMinutes");
        routePayload.remove("preferenceTags");
        routePayload.remove("avoidTags");
        body.put("route", routePayload);
    }

    private void normalizeProfilePayloadForAi(Map<String, Object> body) {
        Object profile = firstNonNull(body.get("user_profile"), body.get("userProfile"), body.get("profileSnapshot"), body.get("profile_snapshot"));
        if (profile != null) {
            body.put("user_profile", profile);
        }
        Object personalization = firstNonNull(body.get("enable_personalization"), body.get("enablePersonalization"));
        if (personalization != null) {
            body.put("enable_personalization", personalization);
        }
        Object shortTermContext = firstNonNull(body.get("short_term_context"), body.get("shortTermContext"));
        if (shortTermContext != null) {
            body.put("short_term_context", shortTermContext);
        }
    }

    private void normalizeOperationalOptionsForAi(Map<String, Object> body) {
        Map<String, Object> options = asMutableMap(normalizeFlexibleObject(body.get("options")));
        if (options == null) {
            return;
        }
        moveOptionToSnake(options, "response_mode", "responseMode");
        moveOptionToSnake(options, "enable_tts", "enableTts");
        moveOptionToSnake(options, "tts_mode", "ttsMode");
        moveOptionToSnake(options, "include_mouth_frames", "includeMouthFrames");
        moveOptionToSnake(options, "include_sources", "includeSources");
        moveOptionToSnake(options, "include_debug", "includeDebug");
        options.remove("route");
        body.put("options", options);
    }

    private void moveOptionToSnake(Map<String, Object> options, String snakeKey, String camelKey) {
        if (options == null) {
            return;
        }
        if (!options.containsKey(snakeKey) && options.containsKey(camelKey)) {
            options.put(snakeKey, options.get(camelKey));
        }
        options.remove(camelKey);
    }

    private Map<String, Object> buildCurrentLocationForAi(Map<String, Object> body, GuideChatRequest request) {
        Map<String, Object> location = asMutableMap(normalizeFlexibleObject(body.get("current_location")));
        if (location == null || location.isEmpty()) {
            location = asMutableMap(normalizeFlexibleObject(request.getLocation()));
        }
        if (location == null) {
            location = new LinkedHashMap<String, Object>();
        }

        Object longitude = firstNonNull(location.get("longitude"), body.get("longitude"), request.getLongitude());
        Object latitude = firstNonNull(location.get("latitude"), body.get("latitude"), request.getLatitude());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (longitude != null && latitude != null) {
            result.put("longitude", longitude);
            result.put("latitude", latitude);
        }
        return result;
    }

    private Map<String, Object> sanitizeLocationContextForAi(Object rawContext) {
        Map<String, Object> source = asMutableMap(normalizeFlexibleObject(rawContext));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (source != null) {
            putMapObjectIfPresent(result, "source", source.get("source"));
            putMapObjectIfPresent(result, "confidence_level", firstNonNull(source.get("confidence_level"), source.get("confidenceLevel")));
            putMapObjectIfPresent(result, "confidence", source.get("confidence"));
        }
        return result;
    }

    private void putMapObjectIfPresent(Map<String, Object> target, String key, Object value) {
        if (target == null || key == null || value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            target.put(key, value);
        }
    }

    private void removeDisallowedAiPayloadFields(Map<String, Object> body) {
        List<String> keys = new ArrayList<String>(body.keySet());
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            if (containsUppercase(key) || !FINAL_AI_PAYLOAD_KEEP_KEYS.contains(key)) {
                body.remove(key);
            }
        }
        body.remove("location");
        body.remove("latitude");
        body.remove("longitude");
        body.remove("is_inside_area");
        body.remove("route_start");
        body.remove("route_start_type");
        body.remove("route_start_location");
        body.remove("route_start_latitude");
        body.remove("route_start_longitude");
        body.remove("start_spot_id");
        body.remove("start_spot_name");
        body.remove("start_latitude");
        body.remove("start_longitude");
        body.remove("current_spot");
        body.remove("route_enabled");
        body.remove("route_intent");
        body.remove("suppress_route");
    }

    private boolean containsUppercase(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isUpperCase(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void applyUserProfileSnapshot(Map<String, Object> body, GuideChatRequest request) {
        if (body == null || request == null) {
            return;
        }

        Map<String, Object> snapshot = profileContextClient.getProfileSnapshotForAi(
                request.getEffectiveUserId(),
                request.getAreaId(),
                firstNotBlank(request.getAreaCode(), request.getParkId()),
                request.getEffectiveScenicId(),
                request.getEffectiveParkName()
        );

        if (snapshot == null || snapshot.isEmpty()) {
            body.put("enablePersonalization", false);
            body.put("enable_personalization", false);
            removeLegacyProfileFields(body);
            body.remove("userProfile");
            body.remove("user_profile");
            body.remove("profileSnapshot");
            body.remove("profile_snapshot");
            log.info("[GuideChat] personalization disabled userId={}, areaCode={}, areaId={}",
                    request.getEffectiveUserId(), firstNotBlank(request.getAreaCode(), request.getParkId()), request.getAreaId());
            return;
        }

        body.put("enablePersonalization", true);
        body.put("enable_personalization", true);
        removeLegacyProfileFields(body);
        body.put("userProfile", snapshot);
        body.put("user_profile", snapshot);
        body.put("profileSnapshot", snapshot);
        body.put("profile_snapshot", snapshot);
        log.info("[GuideChat] personalization enabled userId={}, areaCode={}, areaId={}, profileVersion={}, profileTags={}",
                request.getEffectiveUserId(),
                snapshot.get("areaCode"),
                snapshot.get("areaId"),
                snapshot.get("profileVersion"),
                snapshot.get("profileTags") instanceof List<?> list ? list.size() : 0);
    }

    private void removeLegacyProfileFields(Map<String, Object> body) {
        body.remove("profile");
        body.remove("profile_tags");
        body.remove("profileTags");
        body.remove("short_term_context");
        body.remove("shortTermContext");
    }

    private Map<String, Object> buildTravelParty(String groupSize, String travelType, String visitPreference) {
        Map<String, Object> party = new LinkedHashMap<String, Object>();
        Integer parsedGroupSize = parseGroupSize(groupSize);
        if (parsedGroupSize != null) {
            party.put("groupSize", parsedGroupSize);
        }
        String mergedText = firstNotBlank(groupSize) + " " + firstNotBlank(travelType) + " " + firstNotBlank(visitPreference);
        if (hasText(mergedText)) {
            party.put("withChildren", mergedText.contains("亲子") || mergedText.contains("儿童") || mergedText.contains("孩子"));
            party.put("withElderly", mergedText.contains("老人") || mergedText.contains("长辈") || mergedText.contains("老年"));
        }
        return party.isEmpty() ? null : party;
    }

    private Integer parseGroupSize(String groupSize) {
        String text = firstNotBlank(groupSize);
        if (!hasText(text)) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (!hasText(digits)) {
            return null;
        }
        try {
            return Integer.parseInt(digits.length() > 1 && text.contains("-") ? digits.substring(0, 1) : digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeVisitStatus(String value) {
        return GuideVisitContextResolver.normalizeVisitStatus(value);
    }

    private Map<String, Object> buildRouteContractLocation(GuideChatRequest request) {
        Map<String, Object> location = asMutableMap(normalizeFlexibleObject(request.getLocation()));
        if (location == null) {
            location = new LinkedHashMap<String, Object>();
        }
        if (request.getLongitude() != null && !location.containsKey("longitude")) {
            location.put("longitude", request.getLongitude());
        }
        if (request.getLatitude() != null && !location.containsKey("latitude")) {
            location.put("latitude", request.getLatitude());
        }
        if (request.getAccuracyMeters() != null && !location.containsKey("accuracyMeters")) {
            location.put("accuracyMeters", request.getAccuracyMeters());
            location.put("accuracy_meters", request.getAccuracyMeters());
        }
        return location;
    }

    private Map<String, Object> buildRouteContractCurrentSpot(GuideChatRequest request,
                                                              String currentSpotId,
                                                              String currentSpotName) {
        Map<String, Object> currentSpot = asMutableMap(normalizeFlexibleObject(request.getCurrentSpot()));
        if (currentSpot == null) {
            currentSpot = new LinkedHashMap<String, Object>();
        }
        putMapIfMissing(currentSpot, "spotId", currentSpotId);
        putMapIfMissing(currentSpot, "spot_id", currentSpotId);
        putMapIfMissing(currentSpot, "sceneCode", request.getSceneCode());
        putMapIfMissing(currentSpot, "scene_code", request.getSceneCode());
        putMapIfMissing(currentSpot, "spotName", currentSpotName);
        putMapIfMissing(currentSpot, "spot_name", currentSpotName);
        return currentSpot;
    }

    private void putMapIfMissing(Map<String, Object> map, String key, String value) {
        if (map == null || !hasText(key) || !hasText(value)) {
            return;
        }
        Object current = map.get(key);
        if (current == null || !hasText(String.valueOf(current))) {
            map.put(key, value.trim());
        }
    }

    private void logRouteContractRequest(Map<String, Object> body, boolean routeIntent) {
        if (body == null) {
            return;
        }
        log.info("[RouteContract] request visitStatus={}, visitId={}, conversationId={}, routeIntent={}, areaId={}, areaCode={}, currentSpot={}",
                body.get("visit_status"),
                body.get("visit_id"),
                body.get("conversation_id"),
                routeIntent,
                body.get("area_id"),
                body.get("area_code"),
                body.get("current_spot"));
    }

    private void logRouteContextPayload(Map<String, Object> body, boolean allowRoute) {
        if (body == null || !allowRoute) {
            return;
        }
        if (!"NOT_ARRIVED".equalsIgnoreCase(stringValue(body.get("visit_status")))) {
            return;
        }
        log.info("[RouteContext] pre-arrival route payload visit_status=NOT_ARRIVED, hasVisitId={}, hasCurrentSpot={}, hasCurrentLocation={}, routeEnabled={}",
                hasPayloadValue(body, "visit_id"),
                hasPayloadValue(body, "current_spot_id", "current_spot_name", "current_spot"),
                hasPayloadValue(body, "current_location", "location", "location_context"),
                isRoutePayloadEnabled(body.get("route")));
    }

    private boolean hasPayloadValue(Map<String, Object> body, String... keys) {
        if (body == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value instanceof Map<?, ?> map && !map.isEmpty()) {
                return true;
            }
            if (value instanceof List<?> list && !list.isEmpty()) {
                return true;
            }
            if (value != null && hasText(String.valueOf(value))) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoutePayloadEnabled(Object route) {
        if (route instanceof Map<?, ?> map) {
            return isTruthy(map.get("enabled"));
        }
        return isTruthy(route);
    }

    private void logRouteContractResponse(GuideChatResponse result) {
        if (result == null) {
            return;
        }
        RouteCardDto route = result.getRoute();
        log.info("[RouteContract] response routeIntent={}, routeId={}, routeMode={}, spots={}",
                result.getRouteIntent(),
                route == null ? "" : firstNotBlank(route.routeId),
                route == null ? "" : firstNotBlank(route.routeMode),
                countRouteSpots(route));
    }

    private int countRouteSpots(RouteCardDto route) {
        if (route == null) {
            return 0;
        }
        if (route.spots != null && !route.spots.isEmpty()) {
            return route.spots.size();
        }
        return route.nodes == null ? 0 : route.nodes.size();
    }

    private void logProfileDeltaIgnored(Map<String, Object> responseBody, String userId, String sessionId) {
        Map<String, Object> dataMap = readMap(responseBody, "data");
        Object profileDelta = firstNonNull(
                readObject(dataMap, "profileDelta", "profile_delta"),
                readObject(responseBody, "profileDelta", "profile_delta")
        );
        if (profileDelta != null) {
            log.debug("[GuideChat] ignore profileDelta from AI, userId={}, sessionId={}", userId, sessionId);
        }
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
        return GuideVisitContextResolver.hasRouteIntentText(question);
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
                readString(dataMap, "answer", "reply", "content"),
                readString(responseBody, "answer", "reply", "content")
        ));
        result.setRewrittenQuestion(firstNotBlank(
                readString(dataMap, "rewritten_question", "rewrittenQuestion"),
                readString(responseBody, "rewritten_question", "rewrittenQuestion")
        ));
        result.setIntent(firstNotBlank(
                readString(dataMap, "intent"),
                readString(responseBody, "intent")
        ));
        result.setRouteIntent(firstNonNullBoolean(
                readBoolean(dataMap, "routeIntent", "route_intent"),
                readBoolean(responseBody, "routeIntent", "route_intent")
        ));
        result.setInteractionCategory(firstNotBlank(
                readString(dataMap, "interactionCategory", "interaction_category"),
                readString(responseBody, "interactionCategory", "interaction_category")
        ));
        result.setAnswerStatus(firstNotBlank(
                readString(dataMap, "answerStatus", "answer_status"),
                readString(responseBody, "answerStatus", "answer_status")
        ));
        result.setFallbackReason(firstNotBlank(
                readString(dataMap, "fallbackReason", "fallback_reason"),
                readString(responseBody, "fallbackReason", "fallback_reason")
        ));
        result.setIssueCategory(firstNotBlank(
                readString(dataMap, "issueCategory", "issue_category"),
                readString(responseBody, "issueCategory", "issue_category")
        ));
        result.setIssueType(firstNotBlank(
                readString(dataMap, "issueType", "issue_type"),
                readString(responseBody, "issueType", "issue_type")
        ));
        result.setKnowledgeGapCandidate(firstNonNull(
                readObject(dataMap, "knowledgeGapCandidate", "knowledge_gap_candidate"),
                readObject(responseBody, "knowledgeGapCandidate", "knowledge_gap_candidate")
        ));
        result.setRequiresAdminAction(firstNonNullBoolean(
                readBoolean(dataMap, "requiresAdminAction", "requires_admin_action"),
                readBoolean(responseBody, "requiresAdminAction", "requires_admin_action")
        ));
        result.setGrounding(firstNonNull(
                readObject(dataMap, "grounding"),
                readObject(responseBody, "grounding")
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
        result.setTtsStatus(firstNotBlank(
                readString(dataMap, "tts_status", "ttsStatus"),
                readString(responseBody, "tts_status", "ttsStatus"),
                readString(audioMap, "ttsStatus", "tts_status", "status")
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
        result.setMouthError(firstNotBlank(
                readString(dataMap, "mouthError", "mouth_error"),
                readString(responseBody, "mouthError", "mouth_error"),
                readString(mouthMap, "error", "mouthError", "mouth_error")
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

    private Map<String, Object> readMap(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        if (value instanceof Map<?, ?> rawMap) {
            return objectMapper.convertValue(rawMap, new TypeReference<Map<String, Object>>() {
            });
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

    private Boolean firstNonNullBoolean(Boolean... values) {
        if (values == null) {
            return null;
        }
        for (Boolean value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMutableMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            return objectMapper.convertValue(rawMap, new TypeReference<Map<String, Object>>() {
            });
        }
        return null;
    }

    private Object normalizeFlexibleObject(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            return value;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                return objectMapper.readValue(trimmed, Object.class);
            } catch (Exception ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private String sanitizeBodyForLog(Object body) {
        try {
            Object sanitized = sanitizeLogValue(body, "");
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeLogValue(Object value, String key) {
        if (isSensitiveLogKey(key)) {
            return "***";
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String childKey = String.valueOf(entry.getKey());
                copy.put(childKey, sanitizeLogValue(entry.getValue(), childKey));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : list) {
                copy.add(sanitizeLogValue(item, key));
            }
            return copy;
        }
        return value;
    }

    private boolean isSensitiveLogKey(String key) {
        String text = key == null ? "" : key.toLowerCase();
        return text.contains("token")
                || text.contains("authorization")
                || text.contains("authsession");
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

    private Boolean readBoolean(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = scalarToText(value).toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text) || "y".equals(text) || "是".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text) || "n".equals(text) || "否".equals(text)) {
            return false;
        }
        return null;
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
        payload.put("ttsStatus", firstNotBlank(result.getTtsStatus(), result.getAudioStatus()));
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
        payload.put("error", firstNotBlank(result.getMouthError()));
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
