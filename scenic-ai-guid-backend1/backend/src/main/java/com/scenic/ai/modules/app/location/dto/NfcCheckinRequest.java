package com.scenic.ai.modules.app.location.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class NfcCheckinRequest {

    @JsonAlias({"user_id"})
    private String userId;

    @JsonAlias({"visit_id"})
    private String visitId;

    @JsonAlias({"area_id"})
    private Long areaId;

    @JsonAlias({"marker_code"})
    private String markerCode;

    @JsonAlias({"client_time"})
    private String clientTime;

    @JsonAlias({"network_status"})
    private String networkStatus = "NORMAL";
}
