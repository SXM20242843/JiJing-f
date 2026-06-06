package com.scenic.ai.modules.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class ScenicDetailDto {
    private String id;                // scene_code
    private String parkId;            // area_code
    private String name;              // name
    private String desc;              // intro
    private String time;              // open_time
    private String duration;          // 先预留
    private String badge;             // 标签
    private List<String> tags;        // tags
    private String introduction;      // intro / history_content
    private List<String> highlights;  // highlight 拆分
    private List<String> tips;        // remark 拆分
    private String imageUrl;          // image_url
    private String heat;              // 热度展示

    /**
     * 景点数字人配置。
     *
     * 当前没有景点单独配置表，所以默认继承所属景区的数字人配置。
     */
    private DigitalHumanConfigDto digitalHumanConfig;

    @JsonIgnore
    private String tagsRaw;

    @JsonIgnore
    private String highlightsRaw;

    @JsonIgnore
    private String tipsRaw;
}