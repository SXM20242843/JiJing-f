package com.scenic.ai.modules.app.visit.dto;

import java.math.BigDecimal;

public class VisitEndRequest {

    public Object visit_id;
    public Object visitId;

    public String user_id;
    public String userId;

    public String session_id;
    public String sessionId;

    public Object area_id;
    public Object areaId;

    public String park_name;
    public String parkName;

    public String spot_id;
    public String spotId;

    public String spot_name;
    public String spotName;

    public String end_source;
    public String endSource;
    public String end_reason;
    public String endReason;
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

    public Long getAreaIdValue() {
        return parseLongOrNull(area_id != null ? area_id : areaId);
    }

    public String getParkNameText() {
        if (park_name != null && !park_name.trim().isEmpty()) return park_name.trim();
        if (parkName != null && !parkName.trim().isEmpty()) return parkName.trim();
        return "";
    }

    public String getSpotIdText() {
        if (spot_id != null && !spot_id.trim().isEmpty()) return spot_id.trim();
        if (spotId != null && !spotId.trim().isEmpty()) return spotId.trim();
        return "";
    }

    public String getSpotNameText() {
        if (spot_name != null && !spot_name.trim().isEmpty()) return spot_name.trim();
        if (spotName != null && !spotName.trim().isEmpty()) return spotName.trim();
        return "";
    }

    public String getEndSourceText() {
        if (end_source != null && !end_source.trim().isEmpty()) return end_source.trim();
        if (endSource != null && !endSource.trim().isEmpty()) return endSource.trim();
        if (end_reason != null && !end_reason.trim().isEmpty()) return end_reason.trim();
        if (endReason != null && !endReason.trim().isEmpty()) return endReason.trim();
        if (source != null && !source.trim().isEmpty()) return source.trim();
        if (location_source != null && !location_source.trim().isEmpty()) return location_source.trim();
        if (locationSource != null && !locationSource.trim().isEmpty()) return locationSource.trim();
        if (trigger != null && !trigger.trim().isEmpty()) return trigger.trim();
        return "";
    }

    public String getSourceText() {
        if (source != null && !source.trim().isEmpty()) return source.trim();
        return getEndSourceText();
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
