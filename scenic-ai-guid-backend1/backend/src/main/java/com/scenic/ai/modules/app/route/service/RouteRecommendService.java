package com.scenic.ai.modules.app.route.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.common.config.AiProperties;
import com.scenic.ai.modules.app.route.client.RoutePlanSaveClient;
import com.scenic.ai.modules.app.route.dto.RouteAreaInfo;
import com.scenic.ai.modules.app.route.dto.RouteCardDto;
import com.scenic.ai.modules.app.route.dto.RouteCardNodeDto;
import com.scenic.ai.modules.app.route.dto.RouteRecommendRequest;
import com.scenic.ai.modules.app.route.dto.RouteRecommendResponse;
import com.scenic.ai.modules.app.route.dto.RouteSpotInfo;
import com.scenic.ai.modules.app.route.dto.profile.UserProfileAiContextResponse;
import com.scenic.ai.modules.app.route.dto.save.RoutePlanSaveRequest;
import com.scenic.ai.modules.app.route.entity.TouristRoutePlan;
import com.scenic.ai.modules.app.route.entity.TouristRoutePlanNode;
import com.scenic.ai.modules.app.route.mapper.RoutePlanMapper;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.service.BehaviorEventService;
import com.scenic.ai.modules.app.user.service.UserProfileService;
import com.scenic.ai.modules.chat.dto.GuideChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RouteRecommendService {

    private static final Logger log = LoggerFactory.getLogger(RouteRecommendService.class);

    private static final DateTimeFormatter PLAN_NO_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int DEFAULT_STAY_MIN = 15;
    private static final double WALK_SPEED_M_PER_MIN = 80.0;
    private static final String EVENT_SOURCE = "native-live2d-guide";

    private final RoutePlanMapper routePlanMapper;
    private final BehaviorEventService behaviorEventService;
    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RoutePlanSaveClient routePlanSaveClient;
    private final UserProfileService userProfileService;
    private final Map<String, RouteRoadResult> amapWalkingRouteCache = new ConcurrentHashMap<>();

    public RouteRecommendService(
            RoutePlanMapper routePlanMapper,
            BehaviorEventService behaviorEventService,
            RestTemplate restTemplate,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            RoutePlanSaveClient routePlanSaveClient,
            UserProfileService userProfileService
    ) {
        this.routePlanMapper = routePlanMapper;
        this.behaviorEventService = behaviorEventService;
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.routePlanSaveClient = routePlanSaveClient;
        this.userProfileService = userProfileService;
    }

    @Transactional
    public RouteRecommendResponse recommendRoute(RouteRecommendRequest request, String userId) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        Long areaId = resolveAreaId(request);
        if (areaId == null) {
            throw new IllegalArgumentException("需要用户选择景区后再生成路线（缺少 area_id）");
        }

        List<RouteSpotInfo> candidateSpots = routePlanMapper.selectAvailableSpotsByAreaId(areaId);
        if (candidateSpots == null || candidateSpots.isEmpty()) {
            throw new IllegalArgumentException("当前景区暂无可用景点，无法生成路线");
        }

        RouteAreaInfo areaInfo = routePlanMapper.selectAreaInfoById(areaId);
        RouteStartLocation routeStartLocation = resolveRouteStartLocation(request, areaInfo, candidateSpots);
        List<RouteSpotInfo> routeCandidateSpots = filterRouteCandidateSpots(candidateSpots, routeStartLocation);
        String sessionId = firstNotBlank(request.getSessionIdText(), generateSessionId());
        UserProfileAiContextResponse profileContext = buildProfileContextSafely(
                userId,
                areaId,
                request.getVisitIdValue(),
                sessionId,
                request.getCurrentSpotIdText(),
                request.getCurrentSpotNameText()
        );
        logProfileContext(userId, areaId, profileContext, routeCandidateSpots);

        AiRouteCallResult aiResult = loadRouteResponse(
                request,
                userId,
                sessionId,
                areaId,
                areaInfo,
                routeCandidateSpots,
                routeStartLocation,
                profileContext
        );
        Map<String, Object> rawResponse = aiResult.responseBody;
        Map<String, Object> routeData = unwrapData(rawResponse);
        String answer = buildAnswer(rawResponse, routeData);
        List<ResolvedRouteNode> resolvedNodes = resolveRouteNodes(routeData, answer, routeCandidateSpots);
        RouteMetrics metrics = applyDistanceAndDuration(resolvedNodes, routeStartLocation);
        RouteRoadResult roadResult = buildAmapWalkingRoute(resolvedNodes, routeStartLocation);
        applyRoadMetrics(metrics, roadResult);

        TouristRoutePlan plan = new TouristRoutePlan();
        plan.planNo = generatePlanNo();
        plan.userId = userId;
        plan.sessionId = sessionId;
        plan.visitId = request.getVisitIdValue();
        plan.areaId = areaId;
        plan.routeName = limitLength(resolveRouteName(routeData, resolvedNodes), 255);
        plan.startLongitude = routeStartLocation.longitude;
        plan.startLatitude = routeStartLocation.latitude;
        ResolvedRouteNode lastNode = resolvedNodes.get(resolvedNodes.size() - 1);
        plan.endLongitude = firstDecimal(
                request.getEndLongitudeValue(),
                readDecimal(routeData, "end_longitude", "endLongitude"),
                lastNode.spot.longitude
        );
        plan.endLatitude = firstDecimal(
                request.getEndLatitudeValue(),
                readDecimal(routeData, "end_latitude", "endLatitude"),
                lastNode.spot.latitude
        );
        plan.totalDistanceM = metrics.totalDistanceM;
        plan.estimatedDurationMin = metrics.estimatedDurationMin;
        plan.preferenceSnapshot = writeJsonOrEmpty(buildPreferenceSnapshot(request, profileContext));
        plan.reason = limitLength(firstNotBlank(readString(routeData, "reason", "recommend_reason", "recommendReason"), answer), 2000);
        plan.rawResponseJson = writeJsonOrEmpty(rawResponse);
        plan.planStatus = "GENERATED";

        saveLocalRoutePlanAndEvents(request, userId, sessionId, areaId, plan, resolvedNodes);
        saveRoutePlanToBackend(request, userId, areaId, areaInfo, aiResult.requestBody, rawResponse, profileContext, plan, resolvedNodes);

        RouteRecommendResponse response = new RouteRecommendResponse();
        response.answer = answer;
        response.route = toRouteCard(plan, resolvedNodes, roadResult, routeData);
        return response;
    }

    public ChatRouteContext enrichChatRouteRequest(
            GuideChatRequest chatRequest,
            String userId,
            Map<String, Object> body
    ) {
        ChatRouteContext context = new ChatRouteContext();
        context.routeIntent = isChatRouteIntent(chatRequest);
        if (!context.routeIntent || body == null) {
            return context;
        }

        // 判断是否是真正的现场路线上下文
        boolean isOnsiteRouteContext = isOnsiteRouteRequest(chatRequest);
        log.info("[AI Route Forward] isOnsiteRouteContext={}, visitStatus={}, isInsideArea={}, routeStartType={}, mode={}",
                isOnsiteRouteContext,
                chatRequest.getVisitStatus(),
                chatRequest.getIsInsideArea(),
                chatRequest.getRouteStartType(),
                chatRequest.getMode());

        RouteRecommendRequest routeRequest = toRouteRecommendRequest(chatRequest, userId);
        Long areaId = resolveAreaId(routeRequest);

        // 非现场路线请求：只传 route=true，不增强现场字段
        if (!isOnsiteRouteContext) {
            String parkName = firstNotBlank(
                    routeRequest.getParkNameText(),
                    "景区"
            );
            String areaCode = firstNotBlank(
                    routeRequest.getAreaCodeText(),
                    routeRequest.getParkIdText(),
                    areaId == null ? "" : String.valueOf(areaId)
            );

            body.put("route", true);
            body.put("enable_personalization", true);
            if (areaId != null) {
                body.put("area_id", areaId);
            }
            putIfHasText(body, "area_code", areaCode);
            putIfHasText(body, "park_id", firstNotBlank(routeRequest.getParkIdText(), areaCode));
            putIfHasText(body, "park_name", parkName);
            body.put("route_start_type", "park_entrance");
            // 非现场不传 candidate_spots / current_spot / current_location
            body.put("candidate_spots", new ArrayList<>());
            // 明确清除可能被 buildAiRequestBody 填入的现场字段
            body.remove("current_spot_id");
            body.remove("current_spot_name");
            body.remove("current_location");
            body.remove("route_start_location");
            body.put("visit_status", firstNotBlank(chatRequest.getVisitStatus(), "NOT_IN_AREA"));
            body.put("is_inside_area", chatRequest.getIsInsideArea() == null ? false : chatRequest.getIsInsideArea());

            context.onsiteRoute = false;
            context.areaId = areaId;
            context.routeRequest = routeRequest;
            context.aiRequestBody = new LinkedHashMap<>(body);

            log.info("Chat非现场路线请求: userId={}, areaId={}, parkName={}, visitStatus={}, isInsideArea={}",
                    userId, areaId, parkName,
                    body.get("visit_status"),
                    body.get("is_inside_area"));
            return context;
        }

        // 以下为现场路线增强逻辑（仅 visit_status=IN_AREA 且 is_inside_area=true）
        context.onsiteRoute = true;
        RouteAreaInfo areaInfo = areaId == null ? null : routePlanMapper.selectAreaInfoById(areaId);
        List<RouteSpotInfo> candidateSpots = areaId == null
                ? new ArrayList<>()
                : routePlanMapper.selectAvailableSpotsByAreaId(areaId);
        if (candidateSpots == null) {
            candidateSpots = new ArrayList<>();
        }

        UserProfileAiContextResponse profileContext = areaId == null
                ? null
                : buildProfileContextSafely(
                userId,
                areaId,
                parseLongText(chatRequest.getVisitId()),
                chatRequest.getEffectiveSessionId(),
                chatRequest.getEffectiveCurrentSpotId(),
                chatRequest.getEffectiveCurrentSpotName()
        );
        RouteStartLocation routeStartLocation = candidateSpots.isEmpty()
                ? defaultRouteStartLocation(routeRequest, areaInfo)
                : resolveRouteStartLocation(routeRequest, areaInfo, candidateSpots);
        List<RouteSpotInfo> routeCandidateSpots = filterRouteCandidateSpots(candidateSpots, routeStartLocation);

        String parkName = firstNotBlank(
                routeRequest.getParkNameText(),
                areaInfo == null ? "" : areaInfo.areaName,
                candidateSpots.isEmpty() ? "" : candidateSpots.get(0).areaName,
                "当前景区"
        );
        String areaCode = resolveAiAreaCode(routeRequest, areaInfo, candidateSpots);
        String parkId = firstNotBlank(routeRequest.getParkIdText(), areaCode, areaId == null ? "" : String.valueOf(areaId));
        Map<String, Object> startLocationMap = routeStartLocation.toMap();
        List<String> mergedPreferenceTags = mergePreferenceTags(resolvePreferenceTags(routeRequest), profileContext);

        body.put("route", true);
        body.put("enable_personalization", true);
        body.put("area_id", areaId);
        putIfHasText(body, "area_code", areaCode);
        putIfHasText(body, "park_id", parkId);
        putIfHasText(body, "park_name", parkName);
        body.put("preference_tags", mergedPreferenceTags);
        body.put("candidate_spots", toAiCandidateSpots(routeCandidateSpots));
        body.put("route_start_type", routeStartLocation.type);
        body.put("route_start_location", startLocationMap);
        body.put("current_location", startLocationMap);
        if ("current_spot".equals(routeStartLocation.type)) {
            body.put("current_spot_id", routeStartLocation.spotId == null ? "" : String.valueOf(routeStartLocation.spotId));
            body.put("current_spot_name", routeStartLocation.name);
        } else {
            body.put("current_spot_id", "park_entrance");
            body.put("current_spot_name", parkName + "入口/中心点");
        }
        Integer availableMinutes = routeRequest.getAvailableMinutesValue();
        if (availableMinutes != null) {
            body.put("available_minutes", availableMinutes);
        }
        Object userProfile = profileContext != null && profileContext.getProfile() != null
                ? profileContext.getProfile()
                : routeRequest.getUserProfileValue();
        body.put("user_profile", userProfile == null ? new LinkedHashMap<>() : userProfile);
        body.put("profile_tags", profileContext != null && profileContext.getProfileTags() != null
                ? profileContext.getProfileTags()
                : new ArrayList<>());
        body.put("short_term_context", profileContext != null && profileContext.getShortTermContext() != null
                ? profileContext.getShortTermContext()
                : new LinkedHashMap<>());
        Object recentBehaviors = routeRequest.getRecentBehaviorsValue();
        body.put("recent_behaviors", recentBehaviors == null ? new ArrayList<>() : recentBehaviors);

        context.areaId = areaId;
        context.areaInfo = areaInfo;
        context.candidateSpots = candidateSpots;
        context.routeCandidateSpots = routeCandidateSpots;
        context.routeStartLocation = routeStartLocation;
        context.profileContext = profileContext;
        context.routeRequest = routeRequest;
        context.aiRequestBody = new LinkedHashMap<>(body);

        log.info("Chat路线推荐请求增强: userId={}, areaId={}, parkId={}, parkName={}, hasProfile={}, profileTags={}, preferenceTags={}, candidate_spots={}",
                userId,
                areaId,
                parkId,
                parkName,
                hasProfile(profileContext),
                profileTagCount(profileContext),
                mergedPreferenceTags.size(),
                routeCandidateSpots.size());
        return context;
    }

    private UserProfileAiContextResponse buildProfileContextSafely(
            String userId,
            Long areaId,
            Long visitId,
            String sessionId,
            String currentSpotId,
            String currentSpotName
    ) {
        try {
            return userProfileService.buildAiProfileContext(
                    userId,
                    areaId,
                    visitId,
                    sessionId,
                    currentSpotId,
                    currentSpotName
            );
        } catch (Exception e) {
            log.warn("构建AI画像上下文失败，继续使用默认画像推荐路线: userId={}, areaId={}, error={}",
                    userId, areaId, e.getMessage(), e);
            return null;
        }
    }

    public RouteCardDto standardizeAndSaveChatRoute(
            GuideChatRequest chatRequest,
            String userId,
            String sessionId,
            Map<String, Object> aiResponseBody,
            String answer,
            ChatRouteContext context
    ) {
        if (context == null || !context.routeIntent) {
            return null;
        }
        // 非现场路线请求不保存路线计划到数据库
        if (!context.onsiteRoute) {
            log.info("非现场路线请求跳过路线计划标准化和入库。userId={}, sessionId={}",
                    userId, sessionId);
            return toRawRouteCard(extractRouteDataFromChatResponse(aiResponseBody));
        }

        Map<String, Object> routeData = extractRouteDataFromChatResponse(aiResponseBody);
        if (routeData.isEmpty()) {
            log.info("ChatResponse 未包含结构化路线，尝试根据回答文本和候选景点生成路线卡片。userId={}, sessionId={}",
                    userId, sessionId);
        }
        if (shouldReturnRawRouteWithoutOnsitePlan(routeData)) {
            log.info("ChatResponse route 明确不展示现场路线卡片，跳过路线计划标准化。userId={}, sessionId={}, routeMode={}, shouldShow={}",
                    userId,
                    sessionId,
                    readString(routeData, "route_mode", "routeMode"),
                    readBoolean(routeData, "should_show_route_card", "shouldShowRouteCard"));
            return toRawRouteCard(routeData);
        }

        if (context.areaId == null || context.routeCandidateSpots == null || context.routeCandidateSpots.isEmpty()) {
            log.warn("ChatResponse 包含路线结构，但缺少 areaId 或候选景点，无法标准化路线。userId={}, areaId={}",
                    userId, context.areaId);
            return null;
        }

        String routeAnswer = firstNotBlank(
                answer,
                buildAnswer(aiResponseBody, routeData),
                "已为你生成一条适合当前景区的游览路线。"
        );
        List<ResolvedRouteNode> resolvedNodes = resolveRouteNodes(routeData, routeAnswer, context.routeCandidateSpots);
        if (resolvedNodes.isEmpty()) {
            log.warn("ChatResponse 路线结构未能匹配到真实景点，无法生成路线卡片。userId={}, areaId={}",
                    userId, context.areaId);
            return null;
        }

        RouteMetrics metrics = applyDistanceAndDuration(resolvedNodes, context.routeStartLocation);
        RouteRoadResult roadResult = buildAmapWalkingRoute(resolvedNodes, context.routeStartLocation);
        applyRoadMetrics(metrics, roadResult);

        RouteRecommendRequest routeRequest = context.routeRequest == null
                ? toRouteRecommendRequest(chatRequest, userId)
                : context.routeRequest;

        TouristRoutePlan plan = new TouristRoutePlan();
        plan.planNo = generatePlanNo();
        plan.userId = userId;
        plan.sessionId = sessionId;
        plan.visitId = parseLongText(chatRequest == null ? null : chatRequest.getVisitId());
        plan.areaId = context.areaId;
        plan.routeName = limitLength(resolveRouteName(routeData, resolvedNodes), 255);
        plan.startLongitude = context.routeStartLocation.longitude;
        plan.startLatitude = context.routeStartLocation.latitude;
        ResolvedRouteNode lastNode = resolvedNodes.get(resolvedNodes.size() - 1);
        plan.endLongitude = lastNode.spot.longitude;
        plan.endLatitude = lastNode.spot.latitude;
        plan.totalDistanceM = metrics.totalDistanceM;
        plan.estimatedDurationMin = metrics.estimatedDurationMin;
        plan.preferenceSnapshot = writeJsonOrEmpty(buildPreferenceSnapshot(routeRequest, context.profileContext));
        plan.reason = limitLength(firstNotBlank(readString(routeData, "reason", "recommend_reason", "recommendReason"), routeAnswer), 2000);
        plan.rawResponseJson = writeJsonOrEmpty(aiResponseBody);
        plan.planStatus = "GENERATED";

        saveLocalRoutePlanAndEvents(routeRequest, userId, sessionId, context.areaId, plan, resolvedNodes);
        saveRoutePlanToBackend(
                routeRequest,
                userId,
                context.areaId,
                context.areaInfo,
                context.aiRequestBody,
                aiResponseBody,
                context.profileContext,
                plan,
                resolvedNodes
        );
        return toRouteCard(plan, resolvedNodes, roadResult, routeData);
    }

    public void recordChatRouteRequestWithoutPlan(
            GuideChatRequest chatRequest,
            String userId,
            String sessionId,
            ChatRouteContext context
    ) {
        if (context == null || !context.routeIntent) {
            return;
        }
        // 非现场路线请求不写 ROUTE_REQUEST 行为事件
        if (!context.onsiteRoute) {
            log.info("非现场路线请求跳过 ROUTE_REQUEST 行为事件记录。userId={}, sessionId={}",
                    userId, sessionId);
            return;
        }

        try {
            BehaviorEventRequest event = new BehaviorEventRequest();
            event.userId = userId;
            event.sessionId = sessionId;
            event.visitId = parseLongText(chatRequest == null ? null : chatRequest.getVisitId());
            event.areaId = context.areaId;
            event.spotId = context.routeStartLocation == null ? null : context.routeStartLocation.spotId;
            event.entityType = "ROUTE";
            event.entityId = "";
            event.eventType = "ROUTE_REQUEST";
            event.eventName = "路线推荐";
            event.sourcePage = EVENT_SOURCE;
            event.content = chatRequest == null ? "" : chatRequest.getQuestion();

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("userId", userId);
            extra.put("visitId", event.visitId);
            extra.put("sessionId", sessionId);
            extra.put("areaId", context.areaId);
            extra.put("parkName", chatRequest == null ? "" : chatRequest.getEffectiveParkName());
            extra.put("spotId", event.spotId);
            extra.put("spotName", chatRequest == null ? "" : chatRequest.getEffectiveCurrentSpotName());
            extra.put("source", EVENT_SOURCE);
            extra.put("routeSaved", false);
            event.extra = extra;

            behaviorEventService.addBehaviorEvent(event, userId);
        } catch (Exception e) {
            log.warn("记录Chat路线请求行为失败，不影响问答返回。userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage(), e);
        }
    }

    /**
     * 判断是否是真正的现场路线请求上下文。
     * 只有 visit_status=IN_AREA 且 is_inside_area=true 且 route_start_type=current_spot
     * 才允许执行现场动态路线增强（candidate_spots / current_location / 入库等）。
     */
    private boolean isOnsiteRouteRequest(GuideChatRequest request) {
        if (request == null) {
            return false;
        }
        String visitStatus = firstNotBlank(request.getVisitStatus());
        Boolean isInsideArea = request.getIsInsideArea();
        return "IN_AREA".equalsIgnoreCase(visitStatus)
                && isInsideArea != null && isInsideArea;
    }

    public boolean isChatRouteIntent(GuideChatRequest request) {
        if (request == null) {
            return false;
        }
        if (Boolean.TRUE.equals(request.getRoute())) {
            return true;
        }

        String question = firstNotBlank(request.getQuestion());
        if (question.isEmpty()) {
            return false;
        }
        return question.contains("路线")
                || question.contains("推荐路线")
                || question.contains("怎么逛")
                || question.contains("游览顺序")
                || question.contains("怎么走");
    }

    private Long resolveAreaId(RouteRecommendRequest request) {
        Long areaId = request.getAreaIdValue();
        if (areaId != null) {
            return areaId;
        }

        String areaCode = request.getAreaCodeText();
        if (!areaCode.isEmpty()) {
            return routePlanMapper.selectAreaIdByAreaCode(areaCode);
        }

        String parkId = request.getParkIdText();
        if (parkId.isEmpty()) {
            return null;
        }

        return routePlanMapper.selectAreaIdByParkId(parkId);
    }

    private RouteStartLocation resolveRouteStartLocation(
            RouteRecommendRequest request,
            RouteAreaInfo areaInfo,
            List<RouteSpotInfo> candidateSpots
    ) {
        String requestedStartType = firstNotBlank(request.getRouteStartTypeText());
        String currentSpotId = request.getCurrentSpotIdText();
        RouteSpotInfo currentSpot = null;

        if ("current_spot".equalsIgnoreCase(requestedStartType) && !currentSpotId.isEmpty()) {
            currentSpot = matchSpotByIdOrName(currentSpotId, request.getCurrentSpotNameText(), candidateSpots);
        }

        if (currentSpot != null && currentSpot.latitude != null && currentSpot.longitude != null) {
            RouteStartLocation start = new RouteStartLocation();
            start.type = "current_spot";
            start.spotId = currentSpot.spotId;
            start.sceneCode = currentSpot.sceneCode;
            start.name = currentSpot.spotName;
            start.latitude = currentSpot.latitude;
            start.longitude = currentSpot.longitude;
            return start;
        }

        RouteStartLocation start = new RouteStartLocation();
        start.type = "park_entrance";
        start.name = firstNotBlank(
                areaInfo == null ? "" : areaInfo.areaName,
                candidateSpots.get(0).areaName,
                "景区入口"
        ) + "入口/中心点";

        if (areaInfo != null && areaInfo.latitude != null && areaInfo.longitude != null) {
            start.latitude = areaInfo.latitude;
            start.longitude = areaInfo.longitude;
            return start;
        }

        RouteSpotInfo firstSpot = candidateSpots.get(0);
        start.name = firstNotBlank(areaInfo == null ? "" : areaInfo.areaName, firstSpot.areaName, "景区中心点");
        start.latitude = firstSpot.latitude;
        start.longitude = firstSpot.longitude;

        BigDecimal[] average = averageCoordinate(candidateSpots);
        if (average[0] != null && average[1] != null) {
            start.latitude = average[0];
            start.longitude = average[1];
        }
        return start;
    }

    private RouteStartLocation defaultRouteStartLocation(
            RouteRecommendRequest request,
            RouteAreaInfo areaInfo
    ) {
        String parkName = firstNotBlank(
                request == null ? "" : request.getParkNameText(),
                areaInfo == null ? "" : areaInfo.areaName,
                "景区"
        );
        RouteStartLocation start = new RouteStartLocation();
        start.type = "park_entrance";
        start.name = parkName + "入口/中心点";
        if (areaInfo != null) {
            start.latitude = areaInfo.latitude;
            start.longitude = areaInfo.longitude;
        }
        return start;
    }

    private List<RouteSpotInfo> filterRouteCandidateSpots(
            List<RouteSpotInfo> candidateSpots,
            RouteStartLocation routeStartLocation
    ) {
        if (candidateSpots == null || candidateSpots.isEmpty()) {
            return new ArrayList<>();
        }

        if (routeStartLocation == null
                || !"current_spot".equals(routeStartLocation.type)
                || routeStartLocation.spotId == null
                || candidateSpots.size() <= 1) {
            return candidateSpots;
        }

        List<RouteSpotInfo> result = new ArrayList<>();
        for (RouteSpotInfo spot : candidateSpots) {
            if (!routeStartLocation.spotId.equals(spot.spotId)) {
                result.add(spot);
            }
        }
        return result.isEmpty() ? candidateSpots : result;
    }

    private RouteSpotInfo matchSpotByIdOrName(
            String spotIdOrCode,
            String spotName,
            List<RouteSpotInfo> candidateSpots
    ) {
        String idText = firstNotBlank(spotIdOrCode);
        String nameText = firstNotBlank(spotName);

        for (RouteSpotInfo spot : candidateSpots) {
            if (!idText.isEmpty()
                    && (idText.equals(String.valueOf(spot.spotId))
                    || equalsIgnoreCase(idText, spot.sceneCode))) {
                return spot;
            }
        }

        if (nameText.isEmpty()) {
            return null;
        }

        String normalizedName = normalizeText(nameText);
        for (RouteSpotInfo spot : candidateSpots) {
            if (normalizeText(spot.spotName).equals(normalizedName)) {
                return spot;
            }
        }

        for (RouteSpotInfo spot : candidateSpots) {
            String normalizedSpotName = normalizeText(spot.spotName);
            if (normalizedSpotName.contains(normalizedName) || normalizedName.contains(normalizedSpotName)) {
                return spot;
            }
        }
        return null;
    }

    private RouteRecommendRequest toRouteRecommendRequest(GuideChatRequest chatRequest, String userId) {
        RouteRecommendRequest request = new RouteRecommendRequest();
        if (chatRequest == null) {
            request.userId = userId;
            return request;
        }

        request.userId = firstNotBlank(userId, chatRequest.getEffectiveUserId());
        request.sessionId = chatRequest.getEffectiveSessionId();
        request.visitId = parseLongText(chatRequest.getVisitId());
        request.areaId = chatRequest.getAreaId();
        request.areaCode = firstNotBlank(chatRequest.getAreaCode(), chatRequest.getEffectiveParkId());
        request.parkId = chatRequest.getEffectiveParkId();
        request.parkName = chatRequest.getEffectiveParkName();
        request.routeStartType = chatRequest.getRouteStartType();
        request.currentSpotId = chatRequest.getEffectiveCurrentSpotId();
        request.currentSpotName = chatRequest.getEffectiveCurrentSpotName();
        request.spotId = chatRequest.getEffectiveCurrentSpotId();
        request.spotName = chatRequest.getEffectiveCurrentSpotName();
        request.question = chatRequest.getQuestion();
        request.preferenceTags = chatRequest.getPreferenceTags();
        request.availableMinutes = chatRequest.getAvailableMinutes();
        request.userProfile = chatRequest.getUserProfile();
        request.recentBehaviors = chatRequest.getRecentBehaviors();
        request.clientContext = chatRequest.getClientContext();
        return request;
    }

    private BigDecimal[] averageCoordinate(List<RouteSpotInfo> spots) {
        BigDecimal totalLatitude = BigDecimal.ZERO;
        BigDecimal totalLongitude = BigDecimal.ZERO;
        int count = 0;

        for (RouteSpotInfo spot : spots) {
            if (spot.latitude == null || spot.longitude == null) {
                continue;
            }
            totalLatitude = totalLatitude.add(spot.latitude);
            totalLongitude = totalLongitude.add(spot.longitude);
            count++;
        }

        if (count == 0) {
            return new BigDecimal[]{null, null};
        }

        return new BigDecimal[]{
                totalLatitude.divide(BigDecimal.valueOf(count), 7, RoundingMode.HALF_UP),
                totalLongitude.divide(BigDecimal.valueOf(count), 7, RoundingMode.HALF_UP)
        };
    }

    private AiRouteCallResult loadRouteResponse(
            RouteRecommendRequest request,
            String userId,
            String sessionId,
            Long areaId,
            RouteAreaInfo areaInfo,
            List<RouteSpotInfo> candidateSpots,
            RouteStartLocation routeStartLocation,
            UserProfileAiContextResponse profileContext
    ) {
        Map<String, Object> provided = request.getRouteJsonValue();
        if (provided != null && !provided.isEmpty()) {
            return new AiRouteCallResult(new LinkedHashMap<>(), provided);
        }

        String rawJson = request.getRawResponseJsonText();
        if (!rawJson.isEmpty()) {
            try {
                return new AiRouteCallResult(
                        new LinkedHashMap<>(),
                        objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {})
                );
            } catch (Exception e) {
                log.warn("raw_response_json 不是合法 JSON，使用本地基础路线兜底。error=" + e.getMessage(), e);
                return new AiRouteCallResult(
                        new LinkedHashMap<>(),
                        fallbackRouteResponse("路线推荐结果格式异常，已根据景区景点生成基础路线。")
                );
            }
        }

        String parkName = firstNotBlank(
                request.getParkNameText(),
                areaInfo == null ? "" : areaInfo.areaName,
                candidateSpots.get(0).areaName,
                "当前景区"
        );
        String areaCode = resolveAiAreaCode(request, areaInfo, candidateSpots);
        String parkId = firstNotBlank(request.getParkIdText(), areaCode, String.valueOf(areaId));
        String question = resolveAiQuestion(request, parkName, routeStartLocation);
        Map<String, Object> startLocationMap = routeStartLocation.toMap();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", userId);
        body.put("area_code", areaCode);
        body.put("area_id", areaId);
        body.put("park_id", parkId);
        body.put("park_name", parkName);
        body.put("question", question);
        List<String> mergedPreferenceTags = mergePreferenceTags(resolvePreferenceTags(request), profileContext);
        body.put("preference_tags", mergedPreferenceTags);
        body.put("candidate_spots", toAiCandidateSpots(candidateSpots));
        body.put("route_start_type", routeStartLocation.type);
        if ("current_spot".equals(routeStartLocation.type)) {
            body.put("current_spot_id", routeStartLocation.spotId == null ? "" : String.valueOf(routeStartLocation.spotId));
            body.put("current_spot_name", routeStartLocation.name);
        } else {
            body.put("current_spot_id", "park_entrance");
            body.put("current_spot_name", parkName + "入口/中心点");
        }
        body.put("route_start_location", startLocationMap);
        body.put("current_location", startLocationMap);
        Integer availableMinutes = request.getAvailableMinutesValue();
        if (availableMinutes != null) {
            body.put("available_minutes", availableMinutes);
        }
        Object userProfile = profileContext != null && profileContext.getProfile() != null
                ? profileContext.getProfile()
                : request.getUserProfileValue();
        body.put("user_profile", userProfile == null ? new LinkedHashMap<>() : userProfile);
        body.put("profile_tags", profileContext != null && profileContext.getProfileTags() != null
                ? profileContext.getProfileTags()
                : new ArrayList<>());
        body.put("short_term_context", profileContext != null && profileContext.getShortTermContext() != null
                ? profileContext.getShortTermContext()
                : new LinkedHashMap<>());
        Object recentBehaviors = request.getRecentBehaviorsValue();
        body.put("recent_behaviors", recentBehaviors == null ? new ArrayList<>() : recentBehaviors);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String aiBaseUrl = trimTrailingSlash(aiProperties.getBaseUrl());
        String routeRecommendEndpoint = ensureLeadingSlash(firstNotBlank(
                aiProperties.getRouteRecommendEndpoint(),
                "/api/recommend/route"
        ));
        String url = aiBaseUrl + routeRecommendEndpoint;

        logRouteAiRequest(
                aiBaseUrl,
                routeRecommendEndpoint,
                url,
                userId,
                areaId,
                parkId,
                parkName,
                candidateSpots.size(),
                profileContext,
                mergedPreferenceTags.size(),
                body
        );

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("AI路线推荐返回非成功状态: url={}, status={}, userId={}, areaId={}, parkId={}",
                        url, response.getStatusCode(), userId, areaId, parkId);
                throw new IllegalStateException("AI路线推荐服务返回异常");
            }

            Map<String, Object> responseBody = objectMapper.convertValue(
                    response.getBody(),
                    new TypeReference<Map<String, Object>>() {}
            );
            log.info("AI路线推荐成功: finalAiRouteUrl={}, userId={}, areaId={}, parkId={}, candidate_spots={}",
                    url, userId, areaId, parkId, candidateSpots.size());
            return new AiRouteCallResult(body, responseBody);
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 422) {
                log.warn("AI路线推荐422响应body: " + responseBody);
            }
            log.warn("AI路线推荐调用失败。url=" + url
                    + ", status=" + e.getStatusCode()
                    + ", responseBody=" + responseBody
                    + ", error=" + e.getMessage(), e);
            throw new IllegalStateException("AI路线推荐调用失败", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("AI路线推荐调用失败。url=" + url + ", error=" + e.getMessage(), e);
            throw new IllegalStateException("AI路线推荐调用失败", e);
        } catch (Exception e) {
            log.warn("AI路线推荐返回解析失败。url=" + url + ", error=" + e.getMessage(), e);
            throw new IllegalStateException("AI路线推荐结果格式异常", e);
        }
    }

    private String resolveAiAreaCode(
            RouteRecommendRequest request,
            RouteAreaInfo areaInfo,
            List<RouteSpotInfo> candidateSpots
    ) {
        String parkId = request.getParkIdText();
        if (!parkId.isEmpty() && !isDigits(parkId)) {
            return parkId;
        }

        return firstNotBlank(
                request.getAreaCodeText(),
                areaInfo == null ? "" : areaInfo.areaCode,
                candidateSpots == null || candidateSpots.isEmpty() ? "" : candidateSpots.get(0).areaCode,
                parkId
        );
    }

    private String resolveAiQuestion(
            RouteRecommendRequest request,
            String parkName,
            RouteStartLocation routeStartLocation
    ) {
        String provided = request.getQuestionText();
        if (!provided.isEmpty()) {
            return provided;
        }

        String startName = routeStartLocation == null
                ? parkName + "入口"
                : firstNotBlank(routeStartLocation.name, parkName + "入口");
        return "请根据我当前在" + startName + "的位置，结合候选景点，为我推荐一条合理的游览路线。";
    }

    private List<Map<String, Object>> toAiCandidateSpots(List<RouteSpotInfo> spots) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RouteSpotInfo spot : spots) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("spot_id", spot.spotId == null ? "" : String.valueOf(spot.spotId));
            item.put("spot_name", spot.spotName);
            item.put("latitude", spot.latitude);
            item.put("longitude", spot.longitude);
            item.put("tags", splitTags(spot.tags));
            item.put("recommended_stay_min", firstInteger(spot.recommendedDurationMin, DEFAULT_STAY_MIN));
            item.put("scene_code", spot.sceneCode);
            result.add(item);
        }
        return result;
    }

    private List<String> resolvePreferenceTags(RouteRecommendRequest request) {
        List<String> directTags = request.getPreferenceTagsValue();
        if (directTags != null) {
            return directTags;
        }

        Map<String, Object> snapshot = request.getPreferenceSnapshotValue();
        if (snapshot == null || snapshot.isEmpty()) {
            return new ArrayList<>();
        }

        Object rawTags = readObject(snapshot, "preference_tags", "preferenceTags", "tags", "interestTags");
        if (rawTags instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        }

        if (rawTags != null && !String.valueOf(rawTags).trim().isEmpty()) {
            return splitTags(String.valueOf(rawTags));
        }

        return new ArrayList<>();
    }

    private List<String> mergePreferenceTags(
            List<String> requestTags,
            UserProfileAiContextResponse profileContext
    ) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();

        if (requestTags != null) {
            requestTags.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(merged::add);
        }

        if (profileContext != null
                && profileContext.getProfile() != null
                && profileContext.getProfile().getInterestTags() != null) {
            profileContext.getProfile().getInterestTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(merged::add);
        }

        return new ArrayList<>(merged);
    }

    private List<String> splitTags(String tags) {
        List<String> result = new ArrayList<>();
        if (tags == null || tags.trim().isEmpty()) {
            return result;
        }

        String[] parts = tags.split("[,，/、;；\\s]+");
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private void logRouteAiRequest(
            String aiBaseUrl,
            String routeRecommendEndpoint,
            String finalAiRouteUrl,
            String userId,
            Long areaId,
            String parkId,
            String parkName,
            int candidateSpotCount,
            UserProfileAiContextResponse profileContext,
            int preferenceTagCount,
            Map<String, Object> requestBody
    ) {
        log.info("AI路线推荐请求: aiBaseUrl={}, routeRecommendEndpoint={}, finalAiRouteUrl={}, userId={}, areaId={}, parkId={}, parkName={}, candidate_spots={}, hasProfile={}, profileTags={}, preferenceTags={}, requestKeys={}",
                aiBaseUrl,
                routeRecommendEndpoint,
                finalAiRouteUrl,
                userId,
                areaId,
                parkId,
                parkName,
                candidateSpotCount,
                hasProfile(profileContext),
                profileTagCount(profileContext),
                preferenceTagCount,
                requestBody == null ? new ArrayList<>() : new ArrayList<>(requestBody.keySet()));
    }

    private Map<String, Object> fallbackRouteResponse(String answer) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("route_name", "基础推荐路线");
        route.put("reason", answer);
        route.put("nodes", new ArrayList<>());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("answer", answer);
        response.put("route", route);
        response.put("fallback", true);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRouteDataFromChatResponse(Map<String, Object> responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> dataMap = responseBody;
        Object dataObj = responseBody.get("data");
        if (dataObj instanceof Map<?, ?> rawDataMap) {
            dataMap = (Map<String, Object>) rawDataMap;
        }

        Map<String, Object> routeData = asRouteData(readObject(
                dataMap,
                "route",
                "routePlan",
                "route_plan",
                "route_recommendation",
                "routeRecommendation"
        ));
        if (!routeData.isEmpty()) {
            return routeData;
        }

        routeData = asRouteData(readObject(
                responseBody,
                "route",
                "routePlan",
                "route_plan",
                "route_recommendation",
                "routeRecommendation"
        ));
        if (!routeData.isEmpty()) {
            return routeData;
        }

        Object recommendedSpots = firstNonNull(
                readObject(dataMap, "recommended_spots", "recommendedSpots"),
                readObject(responseBody, "recommended_spots", "recommendedSpots")
        );
        if (recommendedSpots instanceof List<?> list) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", list);
            result.put("reason", readString(dataMap, "reason", "recommend_reason", "recommendReason"));
            result.put("answer", readString(dataMap, "answer", "content"));
            return result;
        }

        if (readObject(dataMap, "spots", "nodes") instanceof List<?>) {
            return dataMap;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asRouteData(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> routeData = objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
            Object nestedRoute = readObject(routeData, "route");
            if (nestedRoute instanceof Map<?, ?> nestedMap) {
                return objectMapper.convertValue(nestedMap, new TypeReference<Map<String, Object>>() {});
            }
            return routeData;
        }
        if (value instanceof List<?> list) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", list);
            return result;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<String, Object> rawResponse) {
        if (rawResponse == null) {
            return new LinkedHashMap<>();
        }

        Object data = rawResponse.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object route = dataMap.get("route");
            if (route instanceof Map<?, ?> routeMap) {
                return (Map<String, Object>) routeMap;
            }
            if (route instanceof List<?> routeList) {
                Map<String, Object> routeData = new LinkedHashMap<>();
                routeData.put("nodes", routeList);
                routeData.put("answer", dataMap.get("answer"));
                routeData.put("reason", dataMap.get("reason"));
                return routeData;
            }
            return (Map<String, Object>) dataMap;
        }

        Object route = rawResponse.get("route");
        if (route instanceof Map<?, ?> routeMap) {
            return (Map<String, Object>) routeMap;
        }
        if (route instanceof List<?> routeList) {
            Map<String, Object> routeData = new LinkedHashMap<>();
            routeData.put("nodes", routeList);
            routeData.put("answer", rawResponse.get("answer"));
            routeData.put("reason", rawResponse.get("reason"));
            return routeData;
        }

        return rawResponse;
    }

    private String buildAnswer(Map<String, Object> rawResponse, Map<String, Object> routeData) {
        String dataText = rawResponse != null && rawResponse.get("data") instanceof String data
                ? data
                : "";
        return firstNotBlank(
                readString(routeData, "answer", "content", "message", "text"),
                readString(rawResponse, "answer", "content", "message", "text"),
                dataText,
                "已为你生成一条适合当前景区的游览路线。"
        );
    }

    private List<ResolvedRouteNode> resolveRouteNodes(
            Map<String, Object> routeData,
            String answer,
            List<RouteSpotInfo> candidateSpots
    ) {
        List<ResolvedRouteNode> result = new ArrayList<>();
        Set<Long> usedSpotIds = new HashSet<>();

        for (Map<String, Object> nodeMap : readNodeMaps(routeData)) {
            RouteSpotInfo spot = matchSpot(nodeMap, candidateSpots);
            if (spot == null || usedSpotIds.contains(spot.spotId)) {
                continue;
            }

            ResolvedRouteNode node = new ResolvedRouteNode();
            node.spot = spot;
            node.guideText = firstNotBlank(
                    readString(nodeMap, "guide_text", "guideText", "reason", "description", "intro"),
                    spot.intro,
                    spot.spotName
            );
            node.recommendedStayMin = firstInteger(
                    readInteger(nodeMap, "recommended_stay_min", "recommendedStayMin", "stay_min", "stayMin"),
                    spot.recommendedDurationMin,
                    DEFAULT_STAY_MIN
            );
            result.add(node);
            usedSpotIds.add(spot.spotId);
        }

        if (result.isEmpty()) {
            result.addAll(resolveNodesFromAnswer(answer, candidateSpots, usedSpotIds));
        }

        if (result.isEmpty()) {
            int max = Math.min(5, candidateSpots.size());
            for (int i = 0; i < max; i++) {
                RouteSpotInfo spot = candidateSpots.get(i);
                ResolvedRouteNode node = new ResolvedRouteNode();
                node.spot = spot;
                node.guideText = firstNotBlank(spot.intro, spot.spotName);
                node.recommendedStayMin = firstInteger(spot.recommendedDurationMin, DEFAULT_STAY_MIN);
                result.add(node);
            }
        }

        for (int i = 0; i < result.size(); i++) {
            result.get(i).order = i + 1;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readNodeMaps(Map<String, Object> routeData) {
        Object nodesObj = readObject(
                routeData,
                "spots",
                "nodes",
                "recommended_spots",
                "recommendedSpots",
                "route",
                "route_nodes",
                "routeNodes",
                "items"
        );
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (!(nodesObj instanceof List<?> list)) {
            return nodes;
        }

        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                nodes.add(objectMapper.convertValue(rawMap, new TypeReference<Map<String, Object>>() {}));
            } else if (item != null) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("spot_name", String.valueOf(item));
                nodes.add(node);
            }
        }
        nodes.sort((left, right) -> {
            Integer leftOrder = readInteger(left, "order", "sort", "index", "sort_order", "sortOrder");
            if (leftOrder == null) {
                leftOrder = readInteger(left, "node_order", "nodeOrder");
            }
            Integer rightOrder = readInteger(right, "order", "sort", "index", "sort_order", "sortOrder");
            if (rightOrder == null) {
                rightOrder = readInteger(right, "node_order", "nodeOrder");
            }
            if (leftOrder == null && rightOrder == null) {
                return 0;
            }
            if (leftOrder == null) {
                return 1;
            }
            if (rightOrder == null) {
                return -1;
            }
            return leftOrder.compareTo(rightOrder);
        });
        return nodes;
    }

    private RouteSpotInfo matchSpot(Map<String, Object> nodeMap, List<RouteSpotInfo> candidateSpots) {
        Long spotId = readLong(nodeMap, "spot_id", "spotId", "id");
        if (spotId != null) {
            for (RouteSpotInfo spot : candidateSpots) {
                if (spotId.equals(spot.spotId)) {
                    return spot;
                }
            }
        }

        String sceneCode = firstNotBlank(
                readString(nodeMap, "spot_id", "spotId"),
                readString(nodeMap, "scene_code", "sceneCode"),
                readString(nodeMap, "scenic_id", "scenicId"),
                readString(nodeMap, "spot_code", "spotCode")
        );
        if (!sceneCode.isEmpty()) {
            for (RouteSpotInfo spot : candidateSpots) {
                if (equalsIgnoreCase(sceneCode, spot.sceneCode)) {
                    return spot;
                }
            }
        }

        BigDecimal latitude = readDecimal(nodeMap, "latitude", "lat");
        BigDecimal longitude = readDecimal(nodeMap, "longitude", "lng", "lon");
        if (latitude != null && longitude != null) {
            RouteSpotInfo nearestSpot = null;
            double nearestDistance = Double.MAX_VALUE;
            for (RouteSpotInfo spot : candidateSpots) {
                double distance = haversineMeters(latitude, longitude, spot.latitude, spot.longitude);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestSpot = spot;
                }
            }
            if (nearestSpot != null && nearestDistance <= 50.0) {
                return nearestSpot;
            }
        }

        String spotName = firstNotBlank(
                readString(nodeMap, "spot_name", "spotName"),
                readString(nodeMap, "node_name", "nodeName"),
                readString(nodeMap, "name", "title", "scenic_name", "scenicName")
        );
        if (spotName.isEmpty()) {
            return null;
        }

        String normalizedInput = normalizeText(spotName);
        for (RouteSpotInfo spot : candidateSpots) {
            if (normalizeText(spot.spotName).equals(normalizedInput)) {
                return spot;
            }
        }

        for (RouteSpotInfo spot : candidateSpots) {
            String normalizedSpotName = normalizeText(spot.spotName);
            if (normalizedInput.contains(normalizedSpotName) || normalizedSpotName.contains(normalizedInput)) {
                return spot;
            }
        }
        return null;
    }

    private List<ResolvedRouteNode> resolveNodesFromAnswer(
            String answer,
            List<RouteSpotInfo> candidateSpots,
            Set<Long> usedSpotIds
    ) {
        List<AnswerSpotMatch> matches = new ArrayList<>();
        String normalizedAnswer = normalizeText(answer);
        if (normalizedAnswer.isEmpty()) {
            return new ArrayList<>();
        }

        for (RouteSpotInfo spot : candidateSpots) {
            if (spot.spotId == null || usedSpotIds.contains(spot.spotId)) {
                continue;
            }

            int index = indexOfSpot(normalizedAnswer, spot);
            if (index >= 0) {
                matches.add(new AnswerSpotMatch(spot, index));
                usedSpotIds.add(spot.spotId);
            }
        }

        matches.sort(Comparator.comparingInt(match -> match.index));
        List<ResolvedRouteNode> result = new ArrayList<>();
        for (AnswerSpotMatch match : matches) {
            ResolvedRouteNode node = new ResolvedRouteNode();
            node.spot = match.spot;
            node.guideText = firstNotBlank(match.spot.intro, match.spot.spotName);
            node.recommendedStayMin = firstInteger(match.spot.recommendedDurationMin, DEFAULT_STAY_MIN);
            result.add(node);
        }
        return result;
    }

    private int indexOfSpot(String normalizedAnswer, RouteSpotInfo spot) {
        String normalizedName = normalizeText(spot.spotName);
        if (normalizedName.length() >= 2 && normalizedAnswer.contains(normalizedName)) {
            return normalizedAnswer.indexOf(normalizedName);
        }

        String normalizedSceneCode = normalizeText(spot.sceneCode);
        if (!normalizedSceneCode.isEmpty() && normalizedAnswer.contains(normalizedSceneCode)) {
            return normalizedAnswer.indexOf(normalizedSceneCode);
        }
        return -1;
    }

    private RouteMetrics applyDistanceAndDuration(
            List<ResolvedRouteNode> nodes,
            RouteStartLocation routeStartLocation
    ) {
        BigDecimal totalDistance = BigDecimal.ZERO;
        int totalStayMin = 0;
        int totalWalkMin = 0;

        ResolvedRouteNode previous = null;
        for (ResolvedRouteNode node : nodes) {
            double distance = previous == null
                    ? haversineMeters(routeStartLocation.latitude, routeStartLocation.longitude, node.spot.latitude, node.spot.longitude)
                    : haversineMeters(previous.spot, node.spot);
            BigDecimal distanceValue = BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
            int walkMin = distance <= 0 ? 0 : Math.max(1, (int) Math.ceil(distance / WALK_SPEED_M_PER_MIN));

            node.distanceFromPrevM = distanceValue;
            node.estimatedWalkMin = walkMin;
            totalDistance = totalDistance.add(distanceValue);
            totalWalkMin += walkMin;
            totalStayMin += firstInteger(node.recommendedStayMin, DEFAULT_STAY_MIN);
            previous = node;
        }

        RouteMetrics metrics = new RouteMetrics();
        metrics.totalDistanceM = totalDistance.setScale(2, RoundingMode.HALF_UP);
        metrics.estimatedDurationMin = totalWalkMin + totalStayMin;
        return metrics;
    }

    private double haversineMeters(RouteSpotInfo from, RouteSpotInfo to) {
        if (from == null || to == null
                || from.latitude == null || from.longitude == null
                || to.latitude == null || to.longitude == null) {
            return 0.0;
        }

        return haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude);
    }

    private double haversineMeters(
            BigDecimal fromLatitude,
            BigDecimal fromLongitude,
            BigDecimal toLatitude,
            BigDecimal toLongitude
    ) {
        if (fromLatitude == null || fromLongitude == null || toLatitude == null || toLongitude == null) {
            return 0.0;
        }

        double earthRadiusM = 6371000.0;
        double lat1 = Math.toRadians(fromLatitude.doubleValue());
        double lat2 = Math.toRadians(toLatitude.doubleValue());
        double deltaLat = Math.toRadians(toLatitude.doubleValue() - fromLatitude.doubleValue());
        double deltaLon = Math.toRadians(toLongitude.doubleValue() - fromLongitude.doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusM * c;
    }

    private String resolveRouteName(Map<String, Object> routeData, List<ResolvedRouteNode> nodes) {
        String provided = firstNotBlank(readString(routeData, "route_name", "routeName", "name", "title"));
        if (!provided.isEmpty()) {
            return provided;
        }

        if (nodes.size() >= 2) {
            return nodes.get(0).spot.spotName + " - " + nodes.get(nodes.size() - 1).spot.spotName + "路线";
        }
        return "AI推荐路线";
    }

    private List<TouristRoutePlanNode> toRoutePlanNodes(Long planId, List<ResolvedRouteNode> resolvedNodes) {
        List<TouristRoutePlanNode> nodes = new ArrayList<>();
        for (ResolvedRouteNode resolvedNode : resolvedNodes) {
            TouristRoutePlanNode node = new TouristRoutePlanNode();
            node.planId = planId;
            node.nodeType = "SPOT";
            node.spotId = resolvedNode.spot.spotId;
            node.facilityId = null;
            node.nodeName = resolvedNode.spot.spotName;
            node.longitude = resolvedNode.spot.longitude;
            node.latitude = resolvedNode.spot.latitude;
            node.sortOrder = resolvedNode.order;
            node.distanceFromPrevM = resolvedNode.distanceFromPrevM;
            node.estimatedWalkMin = resolvedNode.estimatedWalkMin;
            node.recommendedStayMin = resolvedNode.recommendedStayMin;
            node.guideText = resolvedNode.guideText;
            nodes.add(node);
        }
        return nodes;
    }


    private void applyRoadMetrics(RouteMetrics metrics, RouteRoadResult roadResult) {
        if (metrics == null || roadResult == null) {
            return;
        }
        if (roadResult.totalDistanceM != null && roadResult.totalDistanceM.compareTo(BigDecimal.ZERO) > 0) {
            metrics.totalDistanceM = roadResult.totalDistanceM.setScale(2, RoundingMode.HALF_UP);
        }
        if (roadResult.durationSecond > 0) {
            metrics.estimatedDurationMin = Math.max(1, (int) Math.ceil(roadResult.durationSecond / 60.0d));
        }
    }

    private RouteRoadResult buildAmapWalkingRoute(List<ResolvedRouteNode> resolvedNodes,
                                                  RouteStartLocation routeStartLocation) {
        List<RouteRoadPoint> points = buildRoadPlanningPoints(resolvedNodes, routeStartLocation);
        if (points.size() < 2) {
            return RouteRoadResult.fallback(points, "当前路线缺少可用坐标，暂按推荐节点顺序展示");
        }

        if (!isAmapWebRouteEnabled()) {
            return RouteRoadResult.fallback(points, "高德 WebService 未启用，暂按推荐节点顺序展示");
        }

        String amapKey = firstNotBlank(aiProperties.getAmapWebKey());
        if (amapKey.isEmpty()) {
            log.warn("未配置 ai.amap-web-key，无法调用高德 WebService 步行规划，已退回节点连线。");
            return RouteRoadResult.fallback(points, "未配置高德 WebService Key，暂按推荐节点顺序展示");
        }

        String cacheKey = buildRoadCacheKey(points);
        RouteRoadResult cached = amapWalkingRouteCache.get(cacheKey);
        if (cached != null) {
            return cached.copy();
        }

        RouteRoadResult result = new RouteRoadResult();
        result.source = "amap_webservice";
        result.message = "已生成高德步行路线";

        boolean hasFallback = false;
        boolean allFallback = true;

        for (int i = 0; i < points.size() - 1; i++) {
            RouteRoadPoint from = points.get(i);
            RouteRoadPoint to = points.get(i + 1);
            RouteRoadSegment segment = callAmapWalkingSegment(from, to, i);
            if (segment == null) {
                segment = buildFallbackRoadSegment(from, to);
            }

            hasFallback = hasFallback || segment.fallback;
            allFallback = allFallback && segment.fallback;
            result.totalDistanceM = result.totalDistanceM.add(segment.distanceM);
            result.durationSecond += segment.durationSecond;

            if (segment.points != null) {
                for (RouteRoadPoint point : segment.points) {
                    appendRoadPointDedup(result.points, point);
                }
            }

            // 小后端串行请求每一段，避免高德 WebService 也因为瞬时多段请求触发限流。
            if (i < points.size() - 2) {
                sleepQuietly(260L);
            }
        }

        result.totalDistanceM = result.totalDistanceM.setScale(2, RoundingMode.HALF_UP);
        result.partialFallback = hasFallback;
        result.allFallback = allFallback;
        if (allFallback) {
            result.source = "node_fallback";
            result.message = "高德路线暂不可用，已按推荐节点顺序展示";
        } else if (hasFallback) {
            result.message = "部分路段未获取到高德路线，已按节点顺序补齐";
        }

        if (result.points.size() < 2) {
            return RouteRoadResult.fallback(points, "高德路线暂不可用，已按推荐节点顺序展示");
        }

        if (amapWalkingRouteCache.size() > 300) {
            amapWalkingRouteCache.clear();
        }
        amapWalkingRouteCache.put(cacheKey, result.copy());

        log.info("后端高德步行路线生成完成: segments={}, points={}, distanceM={}, durationSecond={}, allFallback={}, partialFallback={}",
                points.size() - 1,
                result.points.size(),
                result.totalDistanceM,
                result.durationSecond,
                result.allFallback,
                result.partialFallback);
        return result;
    }

    private boolean isAmapWebRouteEnabled() {
        return !Boolean.FALSE.equals(aiProperties.getAmapRouteEnabled());
    }

    private List<RouteRoadPoint> buildRoadPlanningPoints(List<ResolvedRouteNode> resolvedNodes,
                                                         RouteStartLocation routeStartLocation) {
        List<RouteRoadPoint> points = new ArrayList<>();
        if (routeStartLocation != null) {
            appendRoadPointDedup(points, RouteRoadPoint.of(
                    firstNotBlank(routeStartLocation.name, "景区入口"),
                    routeStartLocation.latitude,
                    routeStartLocation.longitude
            ));
        }

        for (ResolvedRouteNode node : sortedResolvedNodes(resolvedNodes)) {
            if (node == null || node.spot == null) {
                continue;
            }
            appendRoadPointDedup(points, RouteRoadPoint.of(
                    node.spot.spotName,
                    node.spot.latitude,
                    node.spot.longitude
            ));
        }
        return points;
    }

    private RouteRoadSegment callAmapWalkingSegment(RouteRoadPoint from,
                                                    RouteRoadPoint to,
                                                    int segmentIndex) {
        if (from == null || to == null || !from.isValid() || !to.isValid()) {
            return buildFallbackRoadSegment(from, to);
        }

        try {
            String url = buildAmapWalkingUrl(from, to);
            log.info("调用高德WebService步行规划: segment={}, from={}({}), to={}({})",
                    segmentIndex, from.name, formatAmapLocation(from), to.name, formatAmapLocation(to));

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("高德WebService步行规划HTTP异常: segment={}, status={}",
                        segmentIndex, response.getStatusCode());
                return buildFallbackRoadSegment(from, to);
            }

            Map<String, Object> body = objectMapper.convertValue(
                    response.getBody(),
                    new TypeReference<Map<String, Object>>() {}
            );
            String status = firstNotBlank(readString(body, "status"));
            String info = firstNotBlank(readString(body, "info"));
            String infocode = firstNotBlank(readString(body, "infocode"));
            if (!"1".equals(status)) {
                log.warn("高德WebService步行规划失败: segment={}, status={}, infocode={}, info={}",
                        segmentIndex, status, infocode, info);
                return buildFallbackRoadSegment(from, to);
            }

            RouteRoadSegment segment = parseAmapWalkingSegment(body, from, to);
            if (segment == null || segment.points.size() < 2) {
                log.warn("高德WebService步行规划未返回有效polyline: segment={}, info={}", segmentIndex, info);
                return buildFallbackRoadSegment(from, to);
            }
            segment.fallback = false;
            log.info("高德WebService步行规划成功: segment={}, points={}, distanceM={}, durationSecond={}",
                    segmentIndex, segment.points.size(), segment.distanceM, segment.durationSecond);
            return segment;
        } catch (Exception e) {
            log.warn("调用高德WebService步行规划异常: segment={}, error={}",
                    segmentIndex, e.getMessage(), e);
            return buildFallbackRoadSegment(from, to);
        }
    }

    private String buildAmapWalkingUrl(RouteRoadPoint from, RouteRoadPoint to) {
        String baseUrl = firstNotBlank(
                aiProperties.getAmapWalkDirectionUrl(),
                "https://restapi.amap.com/v3/direction/walking"
        );
        String separator = baseUrl.contains("?") ? "&" : "?";
        // 高德 WebService direction/walking 的 origin / destination 要求格式为：经度,纬度。
        // 这里不要再用 URLEncoder 编码坐标，否则逗号会变成 %2C，部分高德接口会直接返回
        // status=0 / infocode=20000 / INVALID_PARAMS。Key 可以编码，坐标保持原始逗号格式。
        return baseUrl + separator
                + "key=" + urlEncode(aiProperties.getAmapWebKey())
                + "&origin=" + formatAmapLocation(from)
                + "&destination=" + formatAmapLocation(to)
                + "&output=json";
    }

    @SuppressWarnings("unchecked")
    private RouteRoadSegment parseAmapWalkingSegment(Map<String, Object> body,
                                                     RouteRoadPoint from,
                                                     RouteRoadPoint to) {
        Object routeObject = readObject(body, "route");
        if (!(routeObject instanceof Map<?, ?> rawRouteMap)) {
            return buildFallbackRoadSegment(from, to);
        }
        Map<String, Object> routeMap = (Map<String, Object>) rawRouteMap;
        Object pathsObject = readObject(routeMap, "paths");
        if (!(pathsObject instanceof List<?> paths) || paths.isEmpty() || !(paths.get(0) instanceof Map<?, ?> rawPathMap)) {
            return buildFallbackRoadSegment(from, to);
        }

        Map<String, Object> pathMap = (Map<String, Object>) rawPathMap;
        RouteRoadSegment segment = new RouteRoadSegment();
        segment.distanceM = decimalValue(readObject(pathMap, "distance"));
        segment.durationSecond = longValue(readObject(pathMap, "duration"));

        Object stepsObject = readObject(pathMap, "steps");
        if (stepsObject instanceof List<?> steps) {
            BigDecimal stepDistance = BigDecimal.ZERO;
            long stepDuration = 0L;
            for (Object stepObject : steps) {
                if (!(stepObject instanceof Map<?, ?> rawStepMap)) {
                    continue;
                }
                Map<String, Object> stepMap = (Map<String, Object>) rawStepMap;
                stepDistance = stepDistance.add(decimalValue(readObject(stepMap, "distance")));
                stepDuration += longValue(readObject(stepMap, "duration"));
                appendAmapPolylineText(segment.points, readString(stepMap, "polyline"));
            }
            if (segment.distanceM.compareTo(BigDecimal.ZERO) <= 0 && stepDistance.compareTo(BigDecimal.ZERO) > 0) {
                segment.distanceM = stepDistance;
            }
            if (segment.durationSecond <= 0 && stepDuration > 0) {
                segment.durationSecond = stepDuration;
            }
        }

        if (segment.points.size() < 2) {
            appendAmapPolylineText(segment.points, readString(pathMap, "polyline"));
        }
        if (segment.points.size() < 2) {
            appendRoadPointDedup(segment.points, from);
            appendRoadPointDedup(segment.points, to);
            segment.fallback = true;
        }
        if (segment.distanceM.compareTo(BigDecimal.ZERO) <= 0) {
            segment.distanceM = BigDecimal.valueOf(haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        if (segment.durationSecond <= 0) {
            segment.durationSecond = estimateWalkDurationSecond(segment.distanceM);
        }
        return segment;
    }

    private void appendAmapPolylineText(List<RouteRoadPoint> points, String polyline) {
        String text = firstNotBlank(polyline);
        if (text.isEmpty()) {
            return;
        }
        String[] items = text.split(";");
        for (String item : items) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            String[] pair = item.trim().split(",");
            if (pair.length < 2) {
                continue;
            }
            BigDecimal longitude = decimalValue(pair[0]);
            BigDecimal latitude = decimalValue(pair[1]);
            RouteRoadPoint point = RouteRoadPoint.of("", latitude, longitude);
            if (point.isValid()) {
                appendRoadPointDedup(points, point);
            }
        }
    }

    private RouteRoadSegment buildFallbackRoadSegment(RouteRoadPoint from, RouteRoadPoint to) {
        RouteRoadSegment segment = new RouteRoadSegment();
        segment.fallback = true;
        if (from != null && from.isValid()) {
            appendRoadPointDedup(segment.points, from);
        }
        if (to != null && to.isValid()) {
            appendRoadPointDedup(segment.points, to);
        }
        if (from != null && to != null && from.isValid() && to.isValid()) {
            segment.distanceM = BigDecimal.valueOf(haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude))
                    .setScale(2, RoundingMode.HALF_UP);
            segment.durationSecond = estimateWalkDurationSecond(segment.distanceM);
        }
        return segment;
    }

    private long estimateWalkDurationSecond(BigDecimal distanceM) {
        if (distanceM == null || distanceM.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        return Math.max(60L, (long) Math.ceil(distanceM.doubleValue() / 1.15d));
    }

    private String buildRoadCacheKey(List<RouteRoadPoint> points) {
        StringBuilder builder = new StringBuilder();
        for (RouteRoadPoint point : points) {
            if (point == null || !point.isValid()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("|");
            }
            builder.append(point.longitude.setScale(6, RoundingMode.HALF_UP).toPlainString())
                    .append(",")
                    .append(point.latitude.setScale(6, RoundingMode.HALF_UP).toPlainString());
        }
        return builder.toString();
    }

    private void appendRoadPointDedup(List<RouteRoadPoint> points, RouteRoadPoint point) {
        if (points == null || point == null || !point.isValid()) {
            return;
        }
        if (!points.isEmpty()) {
            RouteRoadPoint last = points.get(points.size() - 1);
            if (last != null
                    && last.latitude.subtract(point.latitude).abs().compareTo(BigDecimal.valueOf(0.0000001d)) < 0
                    && last.longitude.subtract(point.longitude).abs().compareTo(BigDecimal.valueOf(0.0000001d)) < 0) {
                return;
            }
        }
        points.add(point);
    }

    private String formatAmapLocation(RouteRoadPoint point) {
        if (point == null || !point.isValid()) {
            return "";
        }
        return point.longitude.stripTrailingZeros().toPlainString()
                + ","
                + point.latitude.stripTrailingZeros().toPlainString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(firstNotBlank(value), StandardCharsets.UTF_8);
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return new BigDecimal(text).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private RouteCardDto toRouteCard(TouristRoutePlan plan, List<ResolvedRouteNode> resolvedNodes) {
        return toRouteCard(plan, resolvedNodes, null);
    }

    private RouteCardDto toRouteCard(TouristRoutePlan plan,
                                     List<ResolvedRouteNode> resolvedNodes,
                                     RouteRoadResult roadResult) {
        return toRouteCard(plan, resolvedNodes, roadResult, null);
    }

    private RouteCardDto toRouteCard(TouristRoutePlan plan,
                                     List<ResolvedRouteNode> resolvedNodes,
                                     RouteRoadResult roadResult,
                                     Map<String, Object> routeData) {
        RouteCardDto route = new RouteCardDto();
        List<ResolvedRouteNode> sortedNodes = sortedResolvedNodes(resolvedNodes);
        route.planId = plan.id == null ? plan.planNo : String.valueOf(plan.id);
        route.routePlanId = plan.id;
        route.routeName = plan.routeName;
        route.title = plan.routeName;
        route.reason = plan.reason;
        route.summary = buildRouteNodeSummary(sortedNodes);
        route.totalDistanceM = plan.totalDistanceM;
        route.distanceText = formatDistanceText(plan.totalDistanceM);
        route.estimatedDurationMin = plan.estimatedDurationMin;
        route.durationText = formatDurationText(plan.estimatedDurationMin);
        route.spotCount = sortedNodes.size();

        List<Map<String, BigDecimal>> nodePolyline = new ArrayList<>();

        for (int index = 0; index < sortedNodes.size(); index++) {
            ResolvedRouteNode node = sortedNodes.get(index);
            RouteCardNodeDto dto = new RouteCardNodeDto();
            dto.order = node.order;
            dto.nodeOrder = node.order;
            dto.nodeType = resolveResponseNodeType(index, sortedNodes.size());
            dto.scenicId = node.spot.sceneCode;
            dto.spotId = node.spot.spotId;
            dto.scenicName = node.spot.spotName;
            dto.spotName = node.spot.spotName;
            dto.latitude = node.spot.latitude;
            dto.longitude = node.spot.longitude;
            dto.guideText = node.guideText;
            dto.recommendedStayMin = node.recommendedStayMin;
            route.nodes.add(dto);

            Map<String, BigDecimal> point = new LinkedHashMap<>();
            point.put("latitude", node.spot.latitude);
            point.put("longitude", node.spot.longitude);
            nodePolyline.add(point);
        }

        route.nodePolyline.addAll(nodePolyline);

        if (roadResult != null && roadResult.points != null && roadResult.points.size() >= 2) {
            List<Map<String, BigDecimal>> roadPolyline = toPolylinePointMaps(roadResult.points);
            route.polyline.addAll(roadPolyline);
            route.routePolyline.addAll(roadPolyline);
            route.mapPolyline.addAll(roadPolyline);
            route.roadPolyline.addAll(roadPolyline);
            route.routeMapReady = !roadResult.allFallback;
            route.mapReady = !roadResult.allFallback;
            route.partialFallback = roadResult.partialFallback;
            route.routeMapMessage = firstNotBlank(roadResult.message,
                    roadResult.allFallback ? "高德路线暂不可用，已按推荐节点顺序展示" : "已生成高德步行路线");
            route.routeMapSource = roadResult.source;
        } else {
            route.polyline.addAll(nodePolyline);
            route.routeMapReady = false;
            route.mapReady = false;
            route.partialFallback = true;
            route.routeMapMessage = "高德路线暂不可用，已按推荐节点顺序展示";
            route.routeMapSource = "node_fallback";
        }

        applyRouteDisplayFields(route, routeData);
        return route;
    }

    private boolean shouldReturnRawRouteWithoutOnsitePlan(Map<String, Object> routeData) {
        if (routeData == null || routeData.isEmpty()) {
            return false;
        }

        Boolean shouldShow = readBoolean(routeData, "should_show_route_card", "shouldShowRouteCard");
        String routeMode = firstNotBlank(readString(routeData, "route_mode", "routeMode"));
        Object spotsObj = readObject(routeData, "spots");
        boolean explicitEmptySpots = spotsObj instanceof List<?> list && list.isEmpty();

        return Boolean.FALSE.equals(shouldShow)
                || "pretrip_template".equalsIgnoreCase(routeMode)
                || explicitEmptySpots;
    }

    private RouteCardDto toRawRouteCard(Map<String, Object> routeData) {
        RouteCardDto route = objectMapper.convertValue(routeData, RouteCardDto.class);
        route.type = firstNotBlank(route.type, "route");
        applyRouteDisplayFields(route, routeData);
        return route;
    }

    @SuppressWarnings("unchecked")
    private void applyRouteDisplayFields(RouteCardDto route, Map<String, Object> routeData) {
        if (route == null || routeData == null || routeData.isEmpty()) {
            return;
        }

        route.routeMode = firstNotBlank(readString(routeData, "route_mode", "routeMode"), route.routeMode);
        route.visitStatus = firstNotBlank(readString(routeData, "visit_status", "visitStatus"), route.visitStatus);
        Boolean isOfficialTemplate = readBoolean(routeData, "is_official_template", "isOfficialTemplate");
        if (isOfficialTemplate != null) {
            route.isOfficialTemplate = isOfficialTemplate;
        }
        Boolean shouldShowRouteCard = readBoolean(routeData, "should_show_route_card", "shouldShowRouteCard");
        if (shouldShowRouteCard != null) {
            route.shouldShowRouteCard = shouldShowRouteCard;
        }

        Object spots = readObject(routeData, "spots");
        if (spots instanceof List<?> list) {
            route.spots = new ArrayList<Object>(list);
        }
        Object templateSpotSequence = readObject(routeData, "template_spot_sequence", "templateSpotSequence");
        if (templateSpotSequence instanceof List<?> list) {
            route.templateSpotSequence = new ArrayList<Object>(list);
        }
    }

    private List<Map<String, BigDecimal>> toPolylinePointMaps(List<RouteRoadPoint> points) {
        List<Map<String, BigDecimal>> result = new ArrayList<>();
        if (points == null) {
            return result;
        }
        for (RouteRoadPoint point : points) {
            if (point == null || point.latitude == null || point.longitude == null) {
                continue;
            }
            Map<String, BigDecimal> item = new LinkedHashMap<>();
            item.put("latitude", point.latitude);
            item.put("longitude", point.longitude);
            result.add(item);
        }
        return result;
    }

    private List<ResolvedRouteNode> sortedResolvedNodes(List<ResolvedRouteNode> resolvedNodes) {
        List<ResolvedRouteNode> result = new ArrayList<>();
        if (resolvedNodes == null) {
            return result;
        }
        for (ResolvedRouteNode node : resolvedNodes) {
            if (node != null && node.spot != null) {
                result.add(node);
            }
        }
        result.sort(Comparator.comparingInt(node -> node.order == null ? Integer.MAX_VALUE : node.order));
        return result;
    }

    private String buildRouteNodeSummary(List<ResolvedRouteNode> resolvedNodes) {
        if (resolvedNodes == null || resolvedNodes.isEmpty()) {
            return "";
        }

        List<String> names = new ArrayList<>();
        for (ResolvedRouteNode node : resolvedNodes) {
            if (node != null && node.spot != null && firstNotBlank(node.spot.spotName).length() > 0) {
                names.add(node.spot.spotName);
            }
        }
        return String.join(" → ", names);
    }

    private String formatDistanceText(BigDecimal distanceM) {
        if (distanceM == null) {
            return "";
        }

        if (distanceM.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            BigDecimal km = distanceM.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
            return km.stripTrailingZeros().toPlainString() + " 公里";
        }
        return distanceM.setScale(0, RoundingMode.HALF_UP).toPlainString() + " 米";
    }

    private String formatDurationText(Integer durationMin) {
        if (durationMin == null) {
            return "";
        }
        return durationMin + " 分钟";
    }

    private String resolveResponseNodeType(int index, int size) {
        if (index <= 0) {
            return "start";
        }
        if (size > 1 && index == size - 1) {
            return "end";
        }
        return "waypoint";
    }

    private void saveLocalRoutePlanAndEvents(
            RouteRecommendRequest request,
            String userId,
            String sessionId,
            Long areaId,
            TouristRoutePlan plan,
            List<ResolvedRouteNode> resolvedNodes
    ) {
        int planRows = routePlanMapper.insertRoutePlan(plan);
        if (planRows <= 0 || plan.id == null) {
            throw new IllegalStateException("保存路线方案失败");
        }

        List<TouristRoutePlanNode> nodes = toRoutePlanNodes(plan.id, resolvedNodes);
        routePlanMapper.batchInsertRoutePlanNodes(nodes);
        try {
            recordRouteRequestEvent(request, userId, sessionId, areaId, plan, resolvedNodes);
        } catch (Exception e) {
            log.warn("记录路线推荐行为失败，但不影响 routePlanId 返回: userId={}, areaId={}, planId={}, error={}",
                    userId, areaId, plan.id, e.getMessage(), e);
        }
        log.info("本地路线方案保存成功: userId={}, areaId={}, planId={}, nodes={}",
                userId, areaId, plan.id, nodes.size());
    }

    private void saveRoutePlanToBackend(
            RouteRecommendRequest request,
            String userId,
            Long areaId,
            RouteAreaInfo areaInfo,
            Map<String, Object> aiRequest,
            Map<String, Object> aiResponse,
            UserProfileAiContextResponse profileContext,
            TouristRoutePlan plan,
            List<ResolvedRouteNode> resolvedNodes
    ) {
        try {
            RoutePlanSaveRequest saveRequest = buildRoutePlanSaveRequest(
                    request,
                    userId,
                    areaId,
                    areaInfo,
                    aiRequest,
                    aiResponse,
                    profileContext,
                    plan,
                    resolvedNodes
            );
            routePlanSaveClient.saveRoutePlan(saveRequest);
        } catch (Exception e) {
            log.warn("构建路线保存请求失败，但不影响路线推荐返回: userId={}, areaId={}, error={}",
                    userId, areaId, e.getMessage(), e);
        }
    }

    private RoutePlanSaveRequest buildRoutePlanSaveRequest(
            RouteRecommendRequest originalRequest,
            String userId,
            Long areaId,
            RouteAreaInfo areaInfo,
            Map<String, Object> aiRequest,
            Map<String, Object> aiResponse,
            UserProfileAiContextResponse profileContext,
            TouristRoutePlan plan,
            List<ResolvedRouteNode> resolvedNodes
    ) {
        RoutePlanSaveRequest saveRequest = new RoutePlanSaveRequest();
        saveRequest.setUserId(userId);
        saveRequest.setAreaId(areaId);
        saveRequest.setParkId(firstNotBlank(
                originalRequest.getParkIdText(),
                areaInfo == null ? "" : areaInfo.areaCode,
                String.valueOf(areaId)
        ));
        saveRequest.setParkName(firstNotBlank(
                originalRequest.getParkNameText(),
                areaInfo == null ? "" : areaInfo.areaName
        ));
        saveRequest.setPlanName(plan.routeName);
        saveRequest.setTotalDurationMin(plan.estimatedDurationMin);
        if (plan.totalDistanceM != null) {
            saveRequest.setTotalDistanceKm(plan.totalDistanceM
                    .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP));
        }
        saveRequest.setRecommendReason(plan.reason);
        saveRequest.setPreferenceSnapshot(buildExternalPreferenceSnapshot(originalRequest, aiRequest, profileContext));
        saveRequest.setAiRawResponse(aiResponse);
        saveRequest.setNodes(buildRoutePlanSaveNodes(resolvedNodes));
        return saveRequest;
    }

    private Map<String, Object> buildExternalPreferenceSnapshot(
            RouteRecommendRequest request,
            Map<String, Object> aiRequest,
            UserProfileAiContextResponse profileContext
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requestPreferenceSnapshot", request.getPreferenceSnapshotValue());
        snapshot.put("profileContext", profileContext);
        snapshot.put("aiRequest", aiRequest);
        return snapshot;
    }

    private List<RoutePlanSaveRequest.RoutePlanNodeSaveRequest> buildRoutePlanSaveNodes(
            List<ResolvedRouteNode> resolvedNodes
    ) {
        List<RoutePlanSaveRequest.RoutePlanNodeSaveRequest> nodes = new ArrayList<>();
        if (resolvedNodes == null) {
            return nodes;
        }

        for (ResolvedRouteNode resolvedNode : resolvedNodes) {
            if (resolvedNode == null || resolvedNode.spot == null) {
                continue;
            }

            RoutePlanSaveRequest.RoutePlanNodeSaveRequest node =
                    new RoutePlanSaveRequest.RoutePlanNodeSaveRequest();
            node.setNodeOrder(resolvedNode.order);
            node.setNodeType("SPOT");
            node.setSpotId(resolvedNode.spot.spotId == null ? null : String.valueOf(resolvedNode.spot.spotId));
            node.setSpotName(resolvedNode.spot.spotName);
            node.setLatitude(resolvedNode.spot.latitude);
            node.setLongitude(resolvedNode.spot.longitude);
            node.setRecommendedStayMin(resolvedNode.recommendedStayMin);
            node.setWalkTimeMin(resolvedNode.estimatedWalkMin);
            node.setGuideText(resolvedNode.guideText);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("sceneCode", resolvedNode.spot.sceneCode);
            extra.put("areaCode", resolvedNode.spot.areaCode);
            extra.put("distanceFromPrevM", resolvedNode.distanceFromPrevM);
            node.setExtra(extra);
            nodes.add(node);
        }
        return nodes;
    }

    private Object buildPreferenceSnapshot(
            RouteRecommendRequest request,
            UserProfileAiContextResponse profileContext
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requestPreferenceSnapshot", request.getPreferenceSnapshotValue());
        snapshot.put("profileContext", profileContext);
        return snapshot;
    }

    private void logProfileContext(
            String userId,
            Long areaId,
            UserProfileAiContextResponse profileContext,
            List<RouteSpotInfo> candidateSpots
    ) {
        log.info("路线推荐画像上下文: userId={}, areaId={}, hasProfile={}, profileTags={}, candidateSpots={}",
                userId,
                areaId,
                hasProfile(profileContext),
                profileTagCount(profileContext),
                candidateSpots == null ? 0 : candidateSpots.size());
    }

    private boolean hasProfile(UserProfileAiContextResponse profileContext) {
        return profileContext != null && profileContext.getProfile() != null;
    }

    private int profileTagCount(UserProfileAiContextResponse profileContext) {
        return profileContext != null && profileContext.getProfileTags() != null
                ? profileContext.getProfileTags().size()
                : 0;
    }

    private void recordRouteRequestEvent(
            RouteRecommendRequest request,
            String userId,
            String sessionId,
            Long areaId,
            TouristRoutePlan plan,
            List<ResolvedRouteNode> resolvedNodes
    ) {
        RouteSpotInfo firstSpot = resolvedNodes.isEmpty() ? null : resolvedNodes.get(0).spot;

        BehaviorEventRequest event = new BehaviorEventRequest();
        event.userId = userId;
        event.sessionId = sessionId;
        event.visitId = request.getVisitIdValue();
        event.areaId = areaId;
        event.spotId = firstSpot == null ? null : firstSpot.spotId;
        event.entityType = "ROUTE";
        event.entityId = String.valueOf(plan.id);
        event.eventType = "ROUTE_REQUEST";
        event.eventName = "路线推荐";
        event.sourcePage = EVENT_SOURCE;
        event.content = request.getQuestionText();

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("userId", userId);
        extra.put("visitId", request.getVisitIdValue());
        extra.put("sessionId", sessionId);
        extra.put("areaId", areaId);
        extra.put("parkName", firstSpot == null ? "" : firstSpot.areaName);
        extra.put("spotId", firstSpot == null ? null : firstSpot.spotId);
        extra.put("spotName", firstSpot == null ? "" : firstSpot.spotName);
        extra.put("planId", plan.id);
        extra.put("routeName", plan.routeName);
        extra.put("source", EVENT_SOURCE);
        extra.put("reservedEvents", List.of(
                "route_view",
                "map_card_show",
                "navigation_start",
                "route_spot_click",
                "map_card_expand",
                "map_card_close"
        ));
        event.extra = extra;

        behaviorEventService.addBehaviorEvent(event, userId);
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

    private String readString(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        return value == null ? null : String.valueOf(value);
    }

    private Long readLong(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer readInteger(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean readBoolean(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }

        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private BigDecimal readDecimal(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal firstDecimal(BigDecimal... values) {
        if (values == null) {
            return null;
        }

        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer firstInteger(Integer... values) {
        if (values == null) {
            return null;
        }

        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String writeJsonOrEmpty(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String generatePlanNo() {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        return "ROUTE_" + LocalDateTime.now().format(PLAN_NO_TIME_FORMATTER) + "_" + random;
    }

    private String generateSessionId() {
        return "chat_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void putIfHasText(Map<String, Object> body, String key, String value) {
        if (body != null && value != null && !value.trim().isEmpty()) {
            body.put(key, value.trim());
        }
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

    private String trimTrailingSlash(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String ensureLeadingSlash(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String result = value.trim();
        return result.startsWith("/") ? result : "/" + result;
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

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean isDigits(String value) {
        return value != null && value.matches("\\d+");
    }

    private static class AiRouteCallResult {
        private final Map<String, Object> requestBody;
        private final Map<String, Object> responseBody;

        private AiRouteCallResult(Map<String, Object> requestBody, Map<String, Object> responseBody) {
            this.requestBody = requestBody == null ? new LinkedHashMap<>() : requestBody;
            this.responseBody = responseBody == null ? new LinkedHashMap<>() : responseBody;
        }
    }

    public static class ChatRouteContext {
        private boolean routeIntent;
        private boolean onsiteRoute;
        private Long areaId;
        private RouteAreaInfo areaInfo;
        private List<RouteSpotInfo> candidateSpots = new ArrayList<>();
        private List<RouteSpotInfo> routeCandidateSpots = new ArrayList<>();
        private RouteStartLocation routeStartLocation;
        private UserProfileAiContextResponse profileContext;
        private RouteRecommendRequest routeRequest;
        private Map<String, Object> aiRequestBody = new LinkedHashMap<>();
    }

    private static class ResolvedRouteNode {
        private RouteSpotInfo spot;
        private String guideText;
        private Integer recommendedStayMin;
        private Integer order;
        private BigDecimal distanceFromPrevM;
        private Integer estimatedWalkMin;
    }

    private static class RouteStartLocation {
        private String type;
        private Long spotId;
        private String sceneCode;
        private String name;
        private BigDecimal latitude;
        private BigDecimal longitude;

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("latitude", latitude);
            result.put("longitude", longitude);
            if (spotId != null) {
                result.put("spot_id", String.valueOf(spotId));
            }
            if (sceneCode != null && !sceneCode.trim().isEmpty()) {
                result.put("scenic_id", sceneCode);
            }
            return result;
        }
    }

    private record AnswerSpotMatch(RouteSpotInfo spot, int index) {
    }


    private static class RouteRoadPoint {
        private String name;
        private BigDecimal latitude;
        private BigDecimal longitude;

        private static RouteRoadPoint of(String name, BigDecimal latitude, BigDecimal longitude) {
            RouteRoadPoint point = new RouteRoadPoint();
            point.name = name;
            point.latitude = latitude;
            point.longitude = longitude;
            return point;
        }

        private boolean isValid() {
            if (latitude == null || longitude == null) {
                return false;
            }
            return latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                    && latitude.compareTo(BigDecimal.valueOf(90)) <= 0
                    && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                    && longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
        }

        private RouteRoadPoint copy() {
            return of(name, latitude, longitude);
        }
    }

    private static class RouteRoadSegment {
        private List<RouteRoadPoint> points = new ArrayList<>();
        private BigDecimal distanceM = BigDecimal.ZERO;
        private long durationSecond = 0L;
        private boolean fallback = false;
    }

    private static class RouteRoadResult {
        private List<RouteRoadPoint> points = new ArrayList<>();
        private BigDecimal totalDistanceM = BigDecimal.ZERO;
        private long durationSecond = 0L;
        private boolean partialFallback = false;
        private boolean allFallback = true;
        private String message = "";
        private String source = "node_fallback";

        private static RouteRoadResult fallback(List<RouteRoadPoint> points, String message) {
            RouteRoadResult result = new RouteRoadResult();
            result.source = "node_fallback";
            result.message = message == null ? "" : message;
            result.partialFallback = true;
            result.allFallback = true;
            if (points != null) {
                for (RouteRoadPoint point : points) {
                    if (point != null && point.isValid()) {
                        result.points.add(point.copy());
                    }
                }
            }
            return result;
        }

        private RouteRoadResult copy() {
            RouteRoadResult result = new RouteRoadResult();
            result.totalDistanceM = totalDistanceM;
            result.durationSecond = durationSecond;
            result.partialFallback = partialFallback;
            result.allFallback = allFallback;
            result.message = message;
            result.source = source;
            for (RouteRoadPoint point : points) {
                if (point != null) {
                    result.points.add(point.copy());
                }
            }
            return result;
        }
    }

    private static class RouteMetrics {
        private BigDecimal totalDistanceM;
        private Integer estimatedDurationMin;
    }
}
