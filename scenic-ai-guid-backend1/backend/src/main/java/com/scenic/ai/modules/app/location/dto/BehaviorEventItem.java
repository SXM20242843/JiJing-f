package com.scenic.ai.modules.app.location.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class BehaviorEventItem {

    @JsonAlias({"event_id"})
    private String eventId;

    @JsonAlias({"user_id"})
    private String userId;

    @JsonAlias({"visit_id"})
    private String visitId;

    @JsonAlias({"area_id"})
    private Long areaId;

    @JsonAlias({"spot_id"})
    private Long spotId;

    @JsonAlias({"scene_code"})
    private String sceneCode;

    @JsonAlias({"marker_code"})
    private String markerCode;

    @JsonAlias({"event_type"})
    private String eventType;

    @JsonAlias({"event_name"})
    private String eventName;

    @JsonAlias({"event_time"})
    private String eventTime;
}
