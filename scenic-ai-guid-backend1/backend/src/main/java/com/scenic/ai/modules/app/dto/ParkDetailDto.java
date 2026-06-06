package com.scenic.ai.modules.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class ParkDetailDto {
    private String id;                // area_code
    private String name;              // area_name
    private String desc;              // intro
    private Integer scenicCount;      // 景点数量
    private String heat;              // 热度展示
    private String tag;               // 标签
    private List<String> tags;        // tags 拆分
    private String parkType;          // park_type
    private Boolean isSingleScenic;   // 是否单体景区
    private String introduction;      // history_intro
    private String openInfo;          // open_info
    private String location;          // location
    private String imageUrl;          // image_url

    /**
     * 管理员为该景区配置的数字人信息。
     */
    private DigitalHumanConfigDto digitalHumanConfig;

    @JsonIgnore
    private String tagsRaw;
}