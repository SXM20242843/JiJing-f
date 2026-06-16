package com.scenic.ai.modules.app.route.dto.profile;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class UserProfileAiContextResponse {

    private String userId;

    private Long areaId;

    private String areaCode;

    private Long profileVersion;

    private Profile profile;

    private Profile longTermProfile;

    private List<ProfileTag> profileTags;

    private ShortTermContext shortTermContext;

    private String updatedAt;

    @Data
    public static class Profile {
        private List<String> interestTags;
        private String travelPace;
        private String travelStyle;
        private String physicalLevel;
        private String walkingPreference;
        private String crowdPreference;
        private String companionType;
        private String budgetLevel;
        private String guidePreference;
        private List<String> consumePreference;
        private Integer preferredDurationMin;
        private List<String> avoidTags;
        private List<String> foodPreference;
        private List<String> accessibilityNeed;
        private String profileSummary;
    }

    @Data
    public static class ProfileTag {
        private String dimensionCode;
        private String tagCode;
        private String tagName;
        private BigDecimal score;
        private String source;
        private String reason;
    }

    @Data
    public static class ShortTermContext {
        private String visitId;
        private String sessionId;
        private Map<String, Object> currentSpot;
        private List<String> recentQuestions;
        private List<Map<String, Object>> recentBehaviors;
        private String activeRoutePlanId;
        private List<String> recentAskedKeywords;
        private List<Long> recentVisitedSpotIds;
        private List<Long> recentFavoriteSpotIds;
        private RecentConsumption recentConsumption;
    }

    @Data
    public static class RecentConsumption {
        private BigDecimal totalAmount;
        private List<String> mainTypes;
    }
}
