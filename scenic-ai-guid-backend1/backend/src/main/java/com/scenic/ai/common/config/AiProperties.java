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

    private String routeRecommendEndpoint = "/api/recommend/route";

    private String parseEndpoint;
}
