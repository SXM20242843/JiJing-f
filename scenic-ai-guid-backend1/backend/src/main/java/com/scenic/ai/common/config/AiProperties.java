package com.scenic.ai.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String baseUrl;

    private String textChatEndpoint = "/api/chat";

    private String voiceChatEndpoint = "/api/chat";

    private String ttsStatusEndpoint = "/api/chat/tts/status";

    private String routeRecommendEndpoint = "/api/recommend/route";

    private String parseEndpoint;

    /**
     * 高德 WebService Key。建议在 application.yml 中通过环境变量配置：
     * ai.amap-web-key: ${AMAP_WEB_KEY:}
     */
    private String amapWebKey;

    /**
     * 高德 WebService 步行路径规划接口。
     */
    private String amapWalkDirectionUrl = "https://restapi.amap.com/v3/direction/walking";

    /**
     * 是否启用小后端高德 WebService 算路。
     */
    private Boolean amapRouteEnabled = true;
}
