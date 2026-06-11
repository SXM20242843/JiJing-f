package com.scenic.ai.modules.chat.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.user.service.AppUserService;
import com.scenic.ai.modules.chat.dto.GuideVoiceChatResponse;
import com.scenic.ai.modules.chat.service.GuideVoiceChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/guide/voice")
public class GuideVoiceChatController {

    private final GuideVoiceChatService guideVoiceChatService;
    private final AppUserService appUserService;

    public GuideVoiceChatController(GuideVoiceChatService guideVoiceChatService, AppUserService appUserService) {
        this.guideVoiceChatService = guideVoiceChatService;
        this.appUserService = appUserService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GuideVoiceChatResponse> chat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestPart(value = "audio_file", required = false) MultipartFile audioFile,
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestPart(value = "file", required = false) MultipartFile file,

            // 原有参数...
            @RequestParam(value = "question", required = false) String question,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "session_id", required = false) String sessionIdSnake,
            @RequestParam(value = "scenicName", required = false) String scenicName,
            @RequestParam(value = "scenic_name", required = false) String scenicNameSnake,
            @RequestParam(value = "userId", required = false) String userIdCamel,
            @RequestParam(value = "user_id", required = false) String userIdSnake,
            @RequestParam(value = "visitId", required = false) String visitIdCamel,
            @RequestParam(value = "visit_id", required = false) String visitIdSnake,
            @RequestParam(value = "parkId", required = false) String parkIdCamel,
            @RequestParam(value = "park_id", required = false) String parkIdSnake,
            @RequestParam(value = "parkName", required = false) String parkNameCamel,
            @RequestParam(value = "park_name", required = false) String parkNameSnake,
            @RequestParam(value = "scenicId", required = false) String scenicIdCamel,
            @RequestParam(value = "scenic_id", required = false) String scenicIdSnake,
            @RequestParam(value = "loginUserId", required = false) String loginUserIdCamel,
            @RequestParam(value = "login_user_id", required = false) String loginUserIdSnake,
            @RequestParam(value = "visitorId", required = false) String visitorIdCamel,
            @RequestParam(value = "visitor_id", required = false) String visitorIdSnake,
            @RequestParam(value = "conversationId", required = false) String conversationIdCamel,
            @RequestParam(value = "conversation_id", required = false) String conversationIdSnake,
            @RequestParam(value = "areaCode", required = false) String areaCodeCamel,
            @RequestParam(value = "area_code", required = false) String areaCodeSnake,
            @RequestParam(value = "areaName", required = false) String areaNameCamel,
            @RequestParam(value = "area_name", required = false) String areaNameSnake,
            @RequestParam(value = "areaId", required = false) String areaIdCamel,
            @RequestParam(value = "area_id", required = false) String areaIdSnake,
            @RequestParam(value = "sceneCode", required = false) String sceneCodeCamel,
            @RequestParam(value = "scene_code", required = false) String sceneCodeSnake,
            @RequestParam(value = "sceneName", required = false) String sceneNameCamel,
            @RequestParam(value = "scene_name", required = false) String sceneNameSnake,
            @RequestParam(value = "currentSpotId", required = false) String currentSpotIdCamel,
            @RequestParam(value = "current_spot_id", required = false) String currentSpotIdSnake,
            @RequestParam(value = "currentSpotName", required = false) String currentSpotNameCamel,
            @RequestParam(value = "current_spot_name", required = false) String currentSpotNameSnake,
            @RequestParam(value = "spotId", required = false) String spotIdCamel,
            @RequestParam(value = "spot_id", required = false) String spotIdSnake,
            @RequestParam(value = "spotName", required = false) String spotNameCamel,
            @RequestParam(value = "spot_name", required = false) String spotNameSnake,
            @RequestParam(value = "voice", required = false) String voice,
            @RequestParam(value = "enableContext", required = false) Boolean enableContextCamel,
            @RequestParam(value = "enable_context", required = false) Boolean enableContextSnake,
            @RequestParam(value = "enableTts", required = false) Boolean enableTtsCamel,
            @RequestParam(value = "enable_tts", required = false) Boolean enableTtsSnake,
            @RequestParam(value = "needVoice", required = false) Boolean needVoiceCamel,
            @RequestParam(value = "need_voice", required = false) Boolean needVoiceSnake,
            @RequestParam(value = "inputType", required = false) String inputTypeCamel,
            @RequestParam(value = "input_type", required = false) String inputTypeSnake,
            @RequestParam(value = "route", required = false) Boolean route,
            @RequestParam(value = "profile", required = false) String profile,
            @RequestParam(value = "digitalHumanConfig", required = false) String digitalHumanConfigCamel,
            @RequestParam(value = "digital_human_config", required = false) String digitalHumanConfigSnake,
            @RequestParam(value = "voiceId", required = false) String voiceIdCamel,
            @RequestParam(value = "voice_id", required = false) String voiceIdSnake,
            @RequestParam(value = "avatarId", required = false) String avatarIdCamel,
            @RequestParam(value = "avatar_id", required = false) String avatarIdSnake,
            @RequestParam(value = "routeStartType", required = false) String routeStartTypeCamel,
            @RequestParam(value = "route_start_type", required = false) String routeStartTypeSnake,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "visitStatus", required = false) String visitStatusCamel,
            @RequestParam(value = "visit_status", required = false) String visitStatusSnake,
            @RequestParam(value = "isInsideArea", required = false) Boolean isInsideAreaCamel,
            @RequestParam(value = "is_inside_area", required = false) Boolean isInsideAreaSnake,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "lat", required = false) String lat,
            @RequestParam(value = "currentLatitude", required = false) String currentLatitudeCamel,
            @RequestParam(value = "current_latitude", required = false) String currentLatitudeSnake,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "lng", required = false) String lng,
            @RequestParam(value = "lon", required = false) String lon,
            @RequestParam(value = "currentLongitude", required = false) String currentLongitudeCamel,
            @RequestParam(value = "current_longitude", required = false) String currentLongitudeSnake,
            @RequestParam(value = "locationContext", required = false) String locationContextCamel,
            @RequestParam(value = "location_context", required = false) String locationContextSnake,

            // ========== 新增三个表单字段 ==========
            @RequestParam(value = "groupSize", required = false) String groupSize,
            @RequestParam(value = "group_size", required = false) String groupSizeSnake,
            @RequestParam(value = "travelType", required = false) String travelType,
            @RequestParam(value = "travel_type", required = false) String travelTypeSnake,
            @RequestParam(value = "visitPreference", required = false) String visitPreference,
            @RequestParam(value = "visit_preference", required = false) String visitPreferenceSnake
            // ===================================
    ) {
        MultipartFile finalAudio = firstFile(audioFile, audio, file);
        if (finalAudio == null || finalAudio.isEmpty()) {
            return new ApiResponse<>(400, "音频文件不能为空", null);
        }

        String finalSessionId = firstNotBlank(sessionId, sessionIdSnake);
        String finalScenicName = firstNotBlank(scenicName, scenicNameSnake);
        String finalUserId = firstNotBlank(userIdCamel, userIdSnake);
        String finalVisitId = firstNotBlank(visitIdCamel, visitIdSnake);
        String finalLoginUserId = firstNotBlank(loginUserIdCamel, loginUserIdSnake);
        String finalVisitorId = firstNotBlank(visitorIdCamel, visitorIdSnake);
        String finalConversationId = firstNotBlank(conversationIdCamel, conversationIdSnake);
        String finalAreaCode = firstNotBlank(areaCodeCamel, areaCodeSnake);
        String finalAreaName = firstNotBlank(areaNameCamel, areaNameSnake);
        Long finalAreaId = parseLong(firstNotBlank(areaIdCamel, areaIdSnake));
        String finalParkId = firstNotBlank(parkIdCamel, parkIdSnake, finalAreaCode);
        String finalParkName = firstNotBlank(parkNameCamel, parkNameSnake, finalAreaName, finalScenicName);
        String finalSceneCode = firstNotBlank(sceneCodeCamel, sceneCodeSnake, scenicIdCamel, scenicIdSnake);
        String finalSceneName = firstNotBlank(sceneNameCamel, sceneNameSnake);
        String finalScenicId = firstNotBlank(scenicIdCamel, scenicIdSnake, finalSceneCode);
        String finalCurrentSpotId = firstNotBlank(currentSpotIdCamel, currentSpotIdSnake, spotIdCamel, spotIdSnake, finalSceneCode);
        String finalCurrentSpotName = firstNotBlank(currentSpotNameCamel, currentSpotNameSnake, spotNameCamel, spotNameSnake, finalSceneName);
        Boolean finalEnableContext = firstBoolean(enableContextCamel, enableContextSnake, true);
        Boolean finalEnableTts = firstBoolean(enableTtsCamel, enableTtsSnake, needVoiceCamel, needVoiceSnake, true);
        String finalInputType = firstNotBlank(inputTypeCamel, inputTypeSnake, "voice");
        String finalDigitalHumanConfig = firstNotBlank(digitalHumanConfigCamel, digitalHumanConfigSnake);
        String finalVoiceId = firstNotBlank(voiceIdCamel, voiceIdSnake, voice);
        String finalAvatarId = firstNotBlank(avatarIdCamel, avatarIdSnake);
        String finalRouteStartType = firstNotBlank(routeStartTypeCamel, routeStartTypeSnake);
        String finalMode = firstNotBlank(mode);
        String finalVisitStatus = firstNotBlank(visitStatusCamel, visitStatusSnake);
        Boolean finalIsInsideArea = firstBoolean(isInsideAreaCamel, isInsideAreaSnake);
        String finalLatitude = firstNotBlank(latitude, lat, currentLatitudeCamel, currentLatitudeSnake);
        String finalLongitude = firstNotBlank(longitude, lng, lon, currentLongitudeCamel, currentLongitudeSnake);
        String finalLocationContext = firstNotBlank(locationContextCamel, locationContextSnake);

        // 解析三个新字段
        String finalGroupSize = firstNotBlank(groupSize, groupSizeSnake);
        String finalTravelType = firstNotBlank(travelType, travelTypeSnake);
        String finalVisitPreference = firstNotBlank(visitPreference, visitPreferenceSnake);

        try {
            finalUserId = appUserService.resolveRequiredUserId(
                    authorization,
                    firstNotBlank(finalUserId, finalLoginUserId, finalVisitorId)
            );
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(400, e.getMessage(), null);
        }

        GuideVoiceChatResponse response = guideVoiceChatService.callVoiceAiService(
                finalAudio,
                question,
                finalSessionId,
                finalScenicName,
                finalVisitId,
                finalUserId,
                finalLoginUserId,
                finalVisitorId,
                finalConversationId,
                finalParkId,
                finalParkName,
                finalAreaId,
                finalSceneCode,
                finalSceneName,
                firstNotBlank(finalCurrentSpotId, finalScenicId),
                finalCurrentSpotName,
                voice,
                finalEnableContext,
                finalEnableTts,
                finalInputType,
                finalGroupSize,
                finalTravelType,
                finalVisitPreference,
                route,
                profile,
                finalDigitalHumanConfig,
                finalVoiceId,
                finalAvatarId,
                finalRouteStartType,
                finalMode,
                finalVisitStatus,
                finalIsInsideArea,
                finalLatitude,
                finalLongitude,
                finalLocationContext
        );

        return ApiResponse.success(response);
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private Boolean firstBoolean(Boolean... values) {
        if (values == null) return null;
        for (Boolean value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private MultipartFile firstFile(MultipartFile... files) {
        if (files == null) {
            return null;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                return file;
            }
        }
        return null;
    }
}
