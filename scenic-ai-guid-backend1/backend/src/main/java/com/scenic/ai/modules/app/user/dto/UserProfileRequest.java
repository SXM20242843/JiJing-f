package com.scenic.ai.modules.app.user.dto;

import java.util.List;

public class UserProfileRequest {

    public String user_id;
    public String userId;

    public String gender;
    public String age_group;
    public String ageGroup;
    public String city;

    public List<String> interest_tags;
    public List<String> interestTags;

    public String travel_preference;
    public String travelPreference;

    public String consumption_level;
    public String consumptionLevel;

    public String travel_pace;
    public String travelPace;

    public String companion_type;
    public String companionType;

    public String walking_preference;
    public String walkingPreference;

    public String guide_preference;
    public String guidePreference;

    public List<String> consume_preference;
    public List<String> consumePreference;

    public String getUserIdText() {
        if (user_id != null && !user_id.trim().isEmpty()) return user_id.trim();
        if (userId != null && !userId.trim().isEmpty()) return userId.trim();
        return "";
    }

    public String getAgeGroupText() {
        if (age_group != null && !age_group.trim().isEmpty()) return age_group.trim();
        if (ageGroup != null && !ageGroup.trim().isEmpty()) return ageGroup.trim();
        return "";
    }

    public List<String> getInterestTagsValue() {
        return interest_tags != null ? interest_tags : interestTags;
    }

    public String getTravelPreferenceText() {
        if (travel_preference != null && !travel_preference.trim().isEmpty()) return travel_preference.trim();
        if (travelPreference != null && !travelPreference.trim().isEmpty()) return travelPreference.trim();
        return "";
    }

    public String getConsumptionLevelText() {
        if (consumption_level != null && !consumption_level.trim().isEmpty()) return consumption_level.trim();
        if (consumptionLevel != null && !consumptionLevel.trim().isEmpty()) return consumptionLevel.trim();
        return "";
    }

    public String getTravelPaceText() {
        if (travel_pace != null && !travel_pace.trim().isEmpty()) return travel_pace.trim();
        if (travelPace != null && !travelPace.trim().isEmpty()) return travelPace.trim();
        return getTravelPreferenceText();
    }

    public String getCompanionTypeText() {
        if (companion_type != null && !companion_type.trim().isEmpty()) return companion_type.trim();
        if (companionType != null && !companionType.trim().isEmpty()) return companionType.trim();
        return "";
    }

    public String getWalkingPreferenceText() {
        if (walking_preference != null && !walking_preference.trim().isEmpty()) return walking_preference.trim();
        if (walkingPreference != null && !walkingPreference.trim().isEmpty()) return walkingPreference.trim();
        return "";
    }

    public String getGuidePreferenceText() {
        if (guide_preference != null && !guide_preference.trim().isEmpty()) return guide_preference.trim();
        if (guidePreference != null && !guidePreference.trim().isEmpty()) return guidePreference.trim();
        return "";
    }

    public List<String> getConsumePreferenceValue() {
        return consume_preference != null ? consume_preference : consumePreference;
    }
}
