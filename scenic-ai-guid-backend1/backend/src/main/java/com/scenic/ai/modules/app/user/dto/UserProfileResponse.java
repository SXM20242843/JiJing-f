package com.scenic.ai.modules.app.user.dto;

import java.util.ArrayList;
import java.util.List;

public class UserProfileResponse {

    public String userId;
    public String gender;
    public String ageGroup;
    public String city;
    public List<String> interestTags = new ArrayList<>();
    public String travelPreference;
    public String consumptionLevel;
    public String travelPace;
    public String companionType;
    public String walkingPreference;
    public String guidePreference;
    public List<String> consumePreference = new ArrayList<>();
    public Boolean profileCompleted;
}
