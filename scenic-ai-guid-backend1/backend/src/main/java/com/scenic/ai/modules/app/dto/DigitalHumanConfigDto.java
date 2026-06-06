package com.scenic.ai.modules.app.dto;

import lombok.Data;

@Data
public class DigitalHumanConfigDto {

    /**
     * 数字人形象 ID
     *
     * 必须和 Android 原生 Live2D 里的 avatarId 对应：
     * guide_female_01
     * guide_female_02
     * guide_female_03
     * guide_male_01
     *
     * guide_default_01 仅作为旧数据兼容，不建议管理端继续使用。
     */
    private String avatarId;

    /**
     * 数字人展示名称
     */
    private String avatarName;

    /**
     * 服装模式
     *
     * 当前 Android 已支持 clothesMode 参数。
     * 你现在数据库里暂无 clothes_mode 字段，所以这里先返回空字符串。
     */
    private String clothesMode;

    /**
     * AI / TTS 音色 ID
     */
    private String voiceId;

    /**
     * 音色展示名称
     */
    private String voiceName;

    /**
     * 进入数字人导览页后的欢迎语
     */
    private String welcomeText;

    /**
     * 以下字段给后续管理端 / APP 展示预留。
     * 当前 APP 打开原生页主要用上面的 avatarId / voiceId / welcomeText。
     */
    private String gender;

    private String clothingName;

    private String modelPath;

    private String previewImage;

    private String remark;
}