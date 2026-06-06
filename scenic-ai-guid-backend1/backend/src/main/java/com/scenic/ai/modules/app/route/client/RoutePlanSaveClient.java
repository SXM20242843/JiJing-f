package com.scenic.ai.modules.app.route.client;

import com.scenic.ai.modules.app.config.BackendProperties;
import com.scenic.ai.modules.app.route.dto.save.RoutePlanSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoutePlanSaveClient {

    private final BackendProperties backendProperties;
    private final RestTemplate restTemplate;

    public void saveRoutePlan(RoutePlanSaveRequest request) {
        if (request == null) {
            return;
        }

        try {
            String url = trimTrailingSlash(backendProperties.getBaseUrl())
                    + ensureLeadingSlash(backendProperties.getRoutePlanSaveEndpoint());
            restTemplate.postForObject(url, request, Object.class);

            log.info("路线方案保存成功: userId={}, areaId={}, parkId={}, nodes={}",
                    request.getUserId(),
                    request.getAreaId(),
                    request.getParkId(),
                    request.getNodes() == null ? 0 : request.getNodes().size());
        } catch (Exception e) {
            log.warn("路线方案保存失败，但不影响本次推荐返回: userId={}, areaId={}, parkId={}, error={}",
                    request.getUserId(),
                    request.getAreaId(),
                    request.getParkId(),
                    e.getMessage());
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String ensureLeadingSlash(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String result = value.trim();
        return result.startsWith("/") ? result : "/" + result;
    }
}
