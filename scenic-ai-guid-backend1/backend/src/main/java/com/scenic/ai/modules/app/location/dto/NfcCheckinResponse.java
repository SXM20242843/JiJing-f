package com.scenic.ai.modules.app.location.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class NfcCheckinResponse {

    @JsonAlias({"area_id"})
    private Long areaId;

    @JsonAlias({"area_name"})
    private String areaName;

    @JsonAlias({"target_type"})
    private String targetType = "SPOT";

    @JsonAlias({"spot_id"})
    private Long spotId;

    @JsonAlias({"facility_id"})
    private Long facilityId;

    @JsonAlias({"scene_code"})
    private String sceneCode;

    @JsonAlias({"spot_name"})
    private String spotName;

    @JsonAlias({"target_name"})
    private String targetName;

    @JsonAlias({"marker_code"})
    private String markerCode;

    @JsonAlias({"marker_name"})
    private String markerName;

    private double confidence = 0.98;

    @JsonAlias({"location_source"})
    private String locationSource = "NFC";

    @JsonAlias({"trigger_action"})
    private String triggerAction = "GUIDE";

    private NfcCheckinGuide guide;

    @Data
    public static class NfcCheckinGuide {
        private String title;
        private String summary;
    }
}
