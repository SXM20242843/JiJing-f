package com.scenic.ai.modules.app.visit.dto;

import java.math.BigDecimal;

public class VisitStartRequest {

    public String user_id;
    public String userId;

    public String park_id;
    public String parkId;
    public String area_code;
    public String areaCode;
    public String park_code;
    public String parkCode;

    public String park_name;
    public String parkName;
    public Long area_id;
    public Long areaId;
    public String area_name;
    public String areaName;

    public String group_size;
    public String groupSize;
    public String travel_people_count;
    public String travelPeopleCount;

    public String travel_type;
    public String travelType;

    public String visit_preference;
    public String visitPreference;
    public String travel_preference;
    public String travelPreference;

    public String start_source;
    public String startSource;
    public String entry_source;
    public String entrySource;
    public String estimated_duration;
    public String estimatedDuration;

    public BigDecimal latitude;
    public BigDecimal longitude;

    public String getUserIdText() {
        if (user_id != null && !user_id.trim().isEmpty()) return user_id.trim();
        if (userId != null && !userId.trim().isEmpty()) return userId.trim();
        return "";
    }

    public String getParkIdText() {
        if (park_id != null && !park_id.trim().isEmpty()) return park_id.trim();
        if (parkId != null && !parkId.trim().isEmpty()) return parkId.trim();
        if (area_code != null && !area_code.trim().isEmpty()) return area_code.trim();
        if (areaCode != null && !areaCode.trim().isEmpty()) return areaCode.trim();
        if (park_code != null && !park_code.trim().isEmpty()) return park_code.trim();
        if (parkCode != null && !parkCode.trim().isEmpty()) return parkCode.trim();
        if (area_id != null) return String.valueOf(area_id);
        if (areaId != null) return String.valueOf(areaId);
        return "";
    }

    public String getAreaCodeText() {
        if (area_code != null && !area_code.trim().isEmpty()) return area_code.trim();
        if (areaCode != null && !areaCode.trim().isEmpty()) return areaCode.trim();
        if (park_code != null && !park_code.trim().isEmpty()) return park_code.trim();
        if (parkCode != null && !parkCode.trim().isEmpty()) return parkCode.trim();
        String parkIdText = getParkIdText();
        if (parkIdText.matches("\\d+")) return "";
        return parkIdText;
    }

    public Long getAreaIdValue() {
        return area_id != null ? area_id : areaId;
    }

    public String getParkNameText() {
        if (park_name != null && !park_name.trim().isEmpty()) return park_name.trim();
        if (parkName != null && !parkName.trim().isEmpty()) return parkName.trim();
        if (area_name != null && !area_name.trim().isEmpty()) return area_name.trim();
        if (areaName != null && !areaName.trim().isEmpty()) return areaName.trim();
        return "";
    }

    public String getGroupSizeText() {
        if (group_size != null && !group_size.trim().isEmpty()) return group_size.trim();
        if (groupSize != null && !groupSize.trim().isEmpty()) return groupSize.trim();
        if (travel_people_count != null && !travel_people_count.trim().isEmpty()) return travel_people_count.trim();
        if (travelPeopleCount != null && !travelPeopleCount.trim().isEmpty()) return travelPeopleCount.trim();
        return "";
    }

    public String getTravelPeopleCountText() {
        if (travel_people_count != null && !travel_people_count.trim().isEmpty()) return travel_people_count.trim();
        if (travelPeopleCount != null && !travelPeopleCount.trim().isEmpty()) return travelPeopleCount.trim();
        return getGroupSizeText();
    }

    public String getTravelTypeText() {
        if (travel_type != null && !travel_type.trim().isEmpty()) return travel_type.trim();
        if (travelType != null && !travelType.trim().isEmpty()) return travelType.trim();
        return "";
    }

    public String getVisitPreferenceText() {
        if (visit_preference != null && !visit_preference.trim().isEmpty()) return visit_preference.trim();
        if (visitPreference != null && !visitPreference.trim().isEmpty()) return visitPreference.trim();
        if (travel_preference != null && !travel_preference.trim().isEmpty()) return travel_preference.trim();
        if (travelPreference != null && !travelPreference.trim().isEmpty()) return travelPreference.trim();
        return "";
    }

    public String getTravelPreferenceText() {
        if (travel_preference != null && !travel_preference.trim().isEmpty()) return travel_preference.trim();
        if (travelPreference != null && !travelPreference.trim().isEmpty()) return travelPreference.trim();
        return getVisitPreferenceText();
    }

    public String getStartSourceText() {
        if (start_source != null && !start_source.trim().isEmpty()) return start_source.trim();
        if (startSource != null && !startSource.trim().isEmpty()) return startSource.trim();
        if (entry_source != null && !entry_source.trim().isEmpty()) return entry_source.trim();
        if (entrySource != null && !entrySource.trim().isEmpty()) return entrySource.trim();
        return "manual";
    }

    public String getEstimatedDurationText() {
        if (estimated_duration != null && !estimated_duration.trim().isEmpty()) return estimated_duration.trim();
        if (estimatedDuration != null && !estimatedDuration.trim().isEmpty()) return estimatedDuration.trim();
        return "";
    }
}
