package com.scenic.ai.modules.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ScenicDto {
    private String id;                // 对前端暴露 scene_code
    private String parkId;            // 对前端暴露 area_code
    private String name;              // name
    private String desc;              // intro
    private String time;              // open_time
    private String duration;          // 先预留，数据库暂无明确字段
    private String badge;             // 标签
    private List<String> tags;        // tags 拆分
    private String imageUrl;          // image_url
    private String heat;              // 热度展示
    private BigDecimal latitude;      // latitude
    private BigDecimal longitude;     // longitude

    /**
     * 景点数字人配置。
     *
     * 当前没有景点单独配置表，所以默认继承所属景区的数字人配置。
     */
    private DigitalHumanConfigDto digitalHumanConfig;

    @JsonIgnore
    private String tagsRaw;
}
