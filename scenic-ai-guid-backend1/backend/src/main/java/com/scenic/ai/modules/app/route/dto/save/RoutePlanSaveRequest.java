package com.scenic.ai.modules.app.route.dto.save;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class RoutePlanSaveRequest {

    private String userId;

    private Long areaId;

    private String parkId;

    private String parkName;

    private String planName;

    private Integer totalDurationMin;

    private BigDecimal totalDistanceKm;

    private String recommendReason;

    private Object preferenceSnapshot;

    private Object aiRawResponse;

    private List<RoutePlanNodeSaveRequest> nodes;

    @Data
    public static class RoutePlanNodeSaveRequest {

        private Integer nodeOrder;

        private String nodeType;

        private String spotId;

        private String spotName;

        private BigDecimal latitude;

        private BigDecimal longitude;

        private Integer recommendedStayMin;

        private Integer walkTimeMin;

        private String guideText;

        private Map<String, Object> extra;
    }
}
