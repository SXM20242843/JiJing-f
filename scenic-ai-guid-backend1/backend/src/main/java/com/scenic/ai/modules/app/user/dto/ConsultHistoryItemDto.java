package com.scenic.ai.modules.app.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConsultHistoryItemDto {

    private Long id;

    private String messageId;

    private String sessionId;

    private String userId;

    private String questionText;

    private String answerText;

    private String areaCode;

    private String areaName;

    private String parkName;

    private String currentSpotId;

    private String currentSpotName;

    private String spotName;

    private String inputType;

    private String source;

    private String sourceName;

    private LocalDateTime createdAt;
}