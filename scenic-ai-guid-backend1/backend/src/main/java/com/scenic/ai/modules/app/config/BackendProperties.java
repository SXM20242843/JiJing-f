package com.scenic.ai.modules.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "scenic.backend")
public class BackendProperties {

    private String baseUrl = "http://127.0.0.1:8080";

    private String profileContextEndpoint = "/api/app/user/profile/ai-context";

    private String routePlanSaveEndpoint = "/api/app/route/plans";
}
