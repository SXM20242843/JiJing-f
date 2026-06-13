package com.scenic.ai.modules.app.offline.dto;

import lombok.Data;

@Data
public class OfflinePackageDto {
    private Long areaId;
    private String areaName;
    private String packageVersion;
    private String packageUrl;
    private Long packageSize;
    private String contentHash;
    private Integer includesAudio;
    private Integer includesMap;
    private Integer spotCount;
    private Integer nfcMarkerCount;
    private Integer routeCount;
    private Integer faqCount;
    private String publishedAt;
}
