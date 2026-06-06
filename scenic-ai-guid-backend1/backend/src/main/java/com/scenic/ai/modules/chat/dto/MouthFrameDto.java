package com.scenic.ai.modules.chat.dto;

import lombok.Data;

@Data
public class MouthFrameDto {
    private Integer t;     // 毫秒时间点
    private Double open;   // 0 ~ 1
    private Double form;   // -1 ~ 1
}