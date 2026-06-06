package com.scenic.ai.modules.app.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ParkSpotDto {

    private String spotId;
    private String scenicId;
    private String spotName;
    private String scenicName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String guideText;
    private String intro;
    private Integer recommendedStayMin;
    private Integer sortOrder;
}
