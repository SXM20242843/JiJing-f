package com.scenic.ai.modules.app.location.dto;

import lombok.Data;

/**
 * scenic_nfc_marker 表 DTO。
 * 字段命名: Java camelCase，数据库 snake_case。
 */
@Data
public class NfcMarkerDto {

    private Long id;
    private Long areaId;
    private String areaName;
    private String targetType;       // AREA / SPOT / FACILITY
    private Long targetId;
    private String targetName;
    private String sceneCode;
    private String markerCode;
    private String markerName;
    private String locationDesc;
    private String guideTitle;
    private String guideSummary;
    private Integer status;

    public boolean isSpot() {
        return "SPOT".equalsIgnoreCase(targetType);
    }

    public boolean isFacility() {
        return "FACILITY".equalsIgnoreCase(targetType);
    }

    public boolean isArea() {
        return "AREA".equalsIgnoreCase(targetType);
    }
}
