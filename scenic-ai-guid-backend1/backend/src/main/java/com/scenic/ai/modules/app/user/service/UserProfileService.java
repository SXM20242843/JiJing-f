package com.scenic.ai.modules.app.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.modules.app.route.dto.profile.UserProfileAiContextResponse;
import com.scenic.ai.modules.app.user.dto.UserProfileRequest;
import com.scenic.ai.modules.app.user.dto.UserProfileResponse;
import com.scenic.ai.modules.app.user.entity.TouristProfileInfo;
import com.scenic.ai.modules.app.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;

    public UserProfileService(UserProfileMapper userProfileMapper, ObjectMapper objectMapper) {
        this.userProfileMapper = userProfileMapper;
        this.objectMapper = objectMapper;
    }

    public UserProfileResponse getProfile(String userId) {
        String safeUserId = trimToEmpty(userId);
        if (safeUserId.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        TouristProfileInfo profileInfo = userProfileMapper.selectProfileInfo(safeUserId);
        if (profileInfo == null) {
            UserProfileResponse empty = new UserProfileResponse();
            empty.userId = safeUserId;
            empty.profileCompleted = false;
            return empty;
        }

        return toResponse(profileInfo);
    }

    @Transactional
    public UserProfileResponse saveProfile(UserProfileRequest request) {
        return saveProfile(request, null);
    }

    @Transactional
    public UserProfileResponse saveProfile(UserProfileRequest request, String requiredUserId) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        String userId = firstNotBlank(requiredUserId, request.getUserIdText());
        if (userId.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        if (userProfileMapper.countUserByUserId(userId) <= 0) {
            throw new IllegalArgumentException("用户不存在");
        }

        String gender = trimToNull(request.gender);
        String ageGroup = trimToNull(request.getAgeGroupText());
        String city = trimToNull(request.city);
        List<String> interestTags = normalizeTags(request.getInterestTagsValue());
        String interestTagsJson = toJson(interestTags);
        String travelPace = normalizeFormValue(request.getTravelPaceText());
        String companionType = normalizeFormValue(request.getCompanionTypeText());
        String walkingPreference = normalizeFormValue(request.getWalkingPreferenceText());
        String guidePreference = normalizeFormValue(request.getGuidePreferenceText());
        List<String> consumePreference = normalizeTags(request.getConsumePreferenceValue());
        String travelStyle = toTravelStyle(travelPace);
        String physicalLevel = toPhysicalLevel(walkingPreference);
        String companionTypeDb = toCompanionType(companionType);
        String foodPreferenceJson = toJson(consumePreference);
        String accessibilityNeedJson = toJsonObject("guidePreference", guidePreference);
        Integer profileCompleted = isProfileCompleted(
                interestTags,
                travelPace,
                companionType,
                walkingPreference,
                guidePreference,
                consumePreference
        ) ? 1 : 0;

        int profileRows;
        if (userProfileMapper.countProfileByUserId(userId) > 0) {
            profileRows = userProfileMapper.updateProfile(
                    userId,
                    city,
                    interestTagsJson,
                    travelStyle,
                    physicalLevel,
                    companionTypeDb,
                    foodPreferenceJson,
                    accessibilityNeedJson
            );
        } else {
            profileRows = userProfileMapper.insertProfile(
                    userId,
                    city,
                    interestTagsJson,
                    travelStyle,
                    physicalLevel,
                    companionTypeDb,
                    foodPreferenceJson,
                    accessibilityNeedJson
            );
        }

        if (profileRows <= 0) {
            throw new IllegalStateException("保存用户画像失败");
        }

        int userRows = userProfileMapper.updateUserProfileFields(
                userId,
                gender,
                ageGroup,
                profileCompleted
        );
        if (userRows <= 0) {
            throw new IllegalStateException("更新用户画像状态失败");
        }

        syncInterestTags(userId, interestTags);

        return getProfile(userId);
    }

    private UserProfileResponse toResponse(TouristProfileInfo profileInfo) {
        UserProfileResponse response = new UserProfileResponse();
        response.userId = profileInfo.userId;
        response.gender = profileInfo.gender;
        response.ageGroup = profileInfo.ageGroup;
        response.city = profileInfo.city;
        response.interestTags = parseTags(profileInfo.interestTagsJson);
        response.travelPreference = profileInfo.travelPreference;
        response.consumptionLevel = profileInfo.consumptionLevel;
        response.travelPace = fromTravelStyle(firstNotBlank(profileInfo.travelStyle, profileInfo.travelPreference));
        response.companionType = fromCompanionType(profileInfo.companionType);
        response.walkingPreference = fromPhysicalLevel(profileInfo.physicalLevel);
        response.consumePreference = parseTags(profileInfo.foodPreferenceJson);
        response.guidePreference = parseGuidePreference(profileInfo.accessibilityNeedJson);
        response.profileCompleted = isProfileCompleted(
                response.interestTags,
                response.travelPace,
                response.companionType,
                response.walkingPreference,
                response.guidePreference,
                response.consumePreference
        );
        return response;
    }

    private boolean isProfileCompleted(
            List<String> interestTags,
            String travelPace,
            String companionType,
            String walkingPreference,
            String guidePreference,
            List<String> consumePreference
    ) {
        return interestTags != null
                && !interestTags.isEmpty()
                && trimToNull(travelPace) != null
                && trimToNull(companionType) != null
                && trimToNull(walkingPreference) != null
                && trimToNull(guidePreference) != null
                && consumePreference != null
                && !consumePreference.isEmpty();
    }

    public UserProfileAiContextResponse buildAiProfileContext(
            String userId,
            Long areaId,
            Long visitId,
            String sessionId,
            String currentSpotId,
            String currentSpotName
    ) {
        String safeUserId = trimToEmpty(userId);
        if (safeUserId.isEmpty()) {
            return null;
        }

        UserProfileResponse profile = getProfile(safeUserId);
        UserProfileAiContextResponse response = new UserProfileAiContextResponse();
        response.setUserId(safeUserId);
        response.setAreaId(areaId);

        UserProfileAiContextResponse.Profile longTermProfile = new UserProfileAiContextResponse.Profile();
        longTermProfile.setInterestTags(profile.interestTags);
        longTermProfile.setTravelPace(profile.travelPace);
        longTermProfile.setTravelStyle(profile.travelPace);
        longTermProfile.setCompanionType(profile.companionType);
        longTermProfile.setWalkingPreference(profile.walkingPreference);
        longTermProfile.setPhysicalLevel(profile.walkingPreference);
        longTermProfile.setGuidePreference(profile.guidePreference);
        longTermProfile.setConsumePreference(profile.consumePreference);
        longTermProfile.setFoodPreference(profile.consumePreference);
        response.setProfile(longTermProfile);
        response.setLongTermProfile(longTermProfile);

        List<UserProfileAiContextResponse.ProfileTag> profileTags = userProfileMapper.selectProfileTags(safeUserId);
        response.setProfileTags(profileTags == null ? Collections.emptyList() : profileTags);

        UserProfileAiContextResponse.ShortTermContext shortTerm = new UserProfileAiContextResponse.ShortTermContext();
        shortTerm.setVisitId(visitId == null ? null : String.valueOf(visitId));
        shortTerm.setSessionId(sessionId);
        List<String> recentQuestions = defaultList(userProfileMapper.selectRecentQuestions(safeUserId));
        shortTerm.setRecentQuestions(recentQuestions);
        shortTerm.setRecentAskedKeywords(recentQuestions);
        shortTerm.setRecentBehaviors(defaultMapList(userProfileMapper.selectRecentBehaviors(safeUserId, areaId)));
        Map<String, Object> latestSpot = userProfileMapper.selectLatestVisitedSpot(safeUserId, areaId);
        String finalSpotId = firstNotBlank(currentSpotId, mapValueText(latestSpot, "spotId"));
        String finalSpotName = firstNotBlank(currentSpotName, mapValueText(latestSpot, "spotName"));
        if (!finalSpotId.isEmpty() || !finalSpotName.isEmpty()) {
            Map<String, Object> currentSpot = new LinkedHashMap<>();
            currentSpot.put("spotId", finalSpotId);
            currentSpot.put("spotName", finalSpotName);
            shortTerm.setCurrentSpot(currentSpot);
        }
        shortTerm.setActiveRoutePlanId(userProfileMapper.selectLatestRoutePlanId(safeUserId, areaId));
        response.setShortTermContext(shortTerm);
        return response;
    }

    public void updateLongTermProfileAfterVisit(String userId, Long visitId) {
        log.info("预留游览结束后长期画像更新入口: userId={}, visitId={}", userId, visitId);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (String tag : tags) {
            String value = trimToNull(tag);
            if (value != null && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private List<String> parseTags(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String parseGuidePreference(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> map = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {
            });
            Object value = map.get("guidePreference");
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? Collections.emptyList() : tags);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String toJsonObject(String key, String value) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (trimToNull(value) != null) {
            map.put(key, value.trim());
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private void syncInterestTags(String userId, List<String> interestTags) {
        userProfileMapper.deleteExplicitTags(userId, "INTEREST");
        for (String tag : interestTags) {
            String tagName = trimToNull(tag);
            if (tagName == null) {
                continue;
            }

            userProfileMapper.upsertExplicitTag(
                    userId,
                    "INTEREST",
                    limitLength(tagName, 64),
                    limitLength(tagName, 100)
            );
        }
    }

    private String normalizeFormValue(String value) {
        return trimToNull(value);
    }

    private String toTravelStyle(String travelPace) {
        String value = trimToEmpty(travelPace).toLowerCase();
        return switch (value) {
            case "slow" -> "RELAXED";
            case "fast" -> "FAST";
            case "standard" -> "DEEP";
            default -> trimToNull(travelPace);
        };
    }

    private String fromTravelStyle(String travelStyle) {
        String value = trimToEmpty(travelStyle).toUpperCase();
        return switch (value) {
            case "RELAXED", "ELDERLY" -> "slow";
            case "FAST" -> "fast";
            case "DEEP", "STANDARD", "PARENT_CHILD" -> "standard";
            default -> trimToNull(travelStyle);
        };
    }

    private String toPhysicalLevel(String walkingPreference) {
        String value = trimToEmpty(walkingPreference).toLowerCase();
        return switch (value) {
            case "less_walk" -> "LOW";
            case "normal" -> "MIDDLE";
            case "long_walk" -> "HIGH";
            default -> trimToNull(walkingPreference);
        };
    }

    private String fromPhysicalLevel(String physicalLevel) {
        String value = trimToEmpty(physicalLevel).toUpperCase();
        return switch (value) {
            case "LOW" -> "less_walk";
            case "MIDDLE" -> "normal";
            case "HIGH" -> "long_walk";
            default -> trimToNull(physicalLevel);
        };
    }

    private String toCompanionType(String companionType) {
        String value = trimToEmpty(companionType).toLowerCase();
        return switch (value) {
            case "solo" -> "ALONE";
            case "friends" -> "FRIENDS";
            case "couple" -> "COUPLE";
            case "family" -> "FAMILY";
            case "elderly" -> "ELDERLY";
            case "children" -> "CHILD";
            default -> trimToNull(companionType);
        };
    }

    private String fromCompanionType(String companionType) {
        String value = trimToEmpty(companionType).toUpperCase();
        return switch (value) {
            case "ALONE" -> "solo";
            case "FRIENDS" -> "friends";
            case "COUPLE" -> "couple";
            case "FAMILY" -> "family";
            case "ELDERLY" -> "elderly";
            case "CHILD" -> "children";
            default -> trimToNull(companionType);
        };
    }

    private List<String> defaultList(List<String> value) {
        return value == null ? Collections.emptyList() : value;
    }

    private List<Map<String, Object>> defaultMapList(List<Map<String, Object>> value) {
        return value == null ? Collections.emptyList() : value;
    }

    private String mapValueText(Map<String, Object> map, String key) {
        if (map == null || key == null || !map.containsKey(key) || map.get(key) == null) {
            return "";
        }

        String value = String.valueOf(map.get(key)).trim();
        return "null".equalsIgnoreCase(value) ? "" : value;
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
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
