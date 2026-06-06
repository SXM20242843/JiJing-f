package com.scenic.ai.modules.app.route.client;

import com.scenic.ai.modules.app.config.BackendProperties;
import com.scenic.ai.modules.app.route.dto.profile.UserProfileAiContextResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileContextClient {

    private final BackendProperties backendProperties;
    private final RestTemplate restTemplate;

    public UserProfileAiContextResponse getProfileContext(String userId, Long areaId) {
        if (userId == null || userId.trim().isEmpty() || areaId == null) {
            log.warn("跳过获取用户画像上下文，userId 或 areaId 为空: userId={}, areaId={}", userId, areaId);
            return null;
        }

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(trimTrailingSlash(backendProperties.getBaseUrl()))
                    .path(ensureLeadingSlash(backendProperties.getProfileContextEndpoint()))
                    .queryParam("userId", userId)
                    .queryParam("areaId", areaId)
                    .toUriString();

            UserProfileAiContextResponse response =
                    restTemplate.getForObject(url, UserProfileAiContextResponse.class);

            log.info("获取用户画像上下文完成: userId={}, areaId={}, hasProfile={}, profileTags={}",
                    userId,
                    areaId,
                    response != null && response.getProfile() != null,
                    response != null && response.getProfileTags() != null
                            ? response.getProfileTags().size()
                            : 0);

            return response;
        } catch (Exception e) {
            log.warn("获取用户画像上下文失败，继续使用默认画像推荐路线: userId={}, areaId={}, error={}",
                    userId, areaId, e.getMessage());
            return null;
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
