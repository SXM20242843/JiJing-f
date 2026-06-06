package com.scenic.ai.modules.app.visit.dto;

import java.math.BigDecimal;

public class SpotEnterRequest {

    public Object visit_id;
    public Object visitId;

    public String user_id;
    public String userId;

    public String session_id;
    public String sessionId;

    public String park_id;
    public String parkId;
    public Object area_id;
    public Object areaId;

    public String park_name;
    public String parkName;

    public String scenic_id;
    public String scenicId;
    public String spot_id;
    public String spotId;

    public String scenic_name;
    public String scenicName;
    public String spot_name;
    public String spotName;

    public String enter_source;
    public String enterSource;
    public String source;
    public String location_source;
    public String locationSource;
    public String trigger;
    public Boolean demo_mode;
    public Boolean demoMode;

    public BigDecimal latitude;
    public BigDecimal longitude;

    public Long getVisitIdValue() {
        return parseLongOrNull(visit_id != null ? visit_id : visitId);
    }

    public String getUserIdText() {
        if (user_id != null && !user_id.trim().isEmpty()) return user_id.trim();
        if (userId != null && !userId.trim().isEmpty()) return userId.trim();
        return "";
    }

    public String getSessionIdText() {
        if (session_id != null && !session_id.trim().isEmpty()) return session_id.trim();
        if (sessionId != null && !sessionId.trim().isEmpty()) return sessionId.trim();
        return "";
    }

    public String getParkIdText() {
        if (park_id != null && !park_id.trim().isEmpty()) return park_id.trim();
        if (parkId != null && !parkId.trim().isEmpty()) return parkId.trim();
        return "";
    }

    public Long getAreaIdValue() {
        return parseLongOrNull(area_id != null ? area_id : areaId);
    }

    public String getParkNameText() {
        if (park_name != null && !park_name.trim().isEmpty()) return park_name.trim();
        if (parkName != null && !parkName.trim().isEmpty()) return parkName.trim();
        return "";
    }

    public String getScenicIdText() {
        if (scenic_id != null && !scenic_id.trim().isEmpty()) return scenic_id.trim();
        if (scenicId != null && !scenicId.trim().isEmpty()) return scenicId.trim();
        if (spot_id != null && !spot_id.trim().isEmpty()) return spot_id.trim();
        if (spotId != null && !spotId.trim().isEmpty()) return spotId.trim();
        return "";
    }

    public String getSpotIdText() {
        if (spot_id != null && !spot_id.trim().isEmpty()) return spot_id.trim();
        if (spotId != null && !spotId.trim().isEmpty()) return spotId.trim();
        return "";
    }

    public String getScenicNameText() {
        if (scenic_name != null && !scenic_name.trim().isEmpty()) return scenic_name.trim();
        if (scenicName != null && !scenicName.trim().isEmpty()) return scenicName.trim();
        if (spot_name != null && !spot_name.trim().isEmpty()) return spot_name.trim();
        if (spotName != null && !spotName.trim().isEmpty()) return spotName.trim();
        return "";
    }

    public String getSpotNameText() {
        if (spot_name != null && !spot_name.trim().isEmpty()) return spot_name.trim();
        if (spotName != null && !spotName.trim().isEmpty()) return spotName.trim();
        if (scenic_name != null && !scenic_name.trim().isEmpty()) return scenic_name.trim();
        if (scenicName != null && !scenicName.trim().isEmpty()) return scenicName.trim();
        return "";
    }

    public String getEnterSourceText() {
        if (enter_source != null && !enter_source.trim().isEmpty()) return enter_source.trim();
        if (enterSource != null && !enterSource.trim().isEmpty()) return enterSource.trim();
        if (source != null && !source.trim().isEmpty()) return source.trim();
        if (location_source != null && !location_source.trim().isEmpty()) return location_source.trim();
        if (locationSource != null && !locationSource.trim().isEmpty()) return locationSource.trim();
        if (trigger != null && !trigger.trim().isEmpty()) return trigger.trim();
        return "ai_guide_click";
    }

    public String getSourceText() {
        if (source != null && !source.trim().isEmpty()) return source.trim();
        return getEnterSourceText();
    }

    public String getLocationSourceText() {
        if (location_source != null && !location_source.trim().isEmpty()) return location_source.trim();
        if (locationSource != null && !locationSource.trim().isEmpty()) return locationSource.trim();
        return "";
    }

    public String getTriggerText() {
        return trigger == null ? "" : trigger.trim();
    }

    public Boolean getDemoModeValue() {
        return demo_mode != null ? demo_mode : demoMode;
    }

    private Long parseLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
