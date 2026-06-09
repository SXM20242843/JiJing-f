package com.scenic.ai.modules.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class MouthFrameDto {
    @JsonAlias({"timeMs", "t", "time"})
    private Long timeMs;   // 毫秒时间点
    @JsonAlias({"mouthOpen", "mouth_open"})
    private Double open;   // 0 ~ 1
    @JsonAlias({"mouthForm", "mouth_form"})
    private Double form;   // -1 ~ 1
}
