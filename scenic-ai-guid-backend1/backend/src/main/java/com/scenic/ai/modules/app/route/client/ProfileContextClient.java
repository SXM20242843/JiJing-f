package com.scenic.ai.modules.app.route.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.modules.app.config.BackendProperties;
import com.scenic.ai.modules.app.route.dto.profile.UserProfileAiContextResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileContextClient {

    private static final long PROFILE_CACHE_TTL_MS = 30L * 60L * 1000L;
    private static final String CACHE_PREFIX = "userProfile:";

    private final BackendProperties backendProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> profileCache = new ConcurrentHashMap<>();

    public UserProfileAiContextResponse getProfileContext(String userId, Long areaId) {
        Map<String, Object> snapshot = getProfileSnapshotForAi(userId, areaId, null, null, null);
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(snapshot, UserProfileAiContextResponse.class);
    }

    public Map<String, Object> getProfileSnapshotForAi(String userId,
                                                       Long areaId,
                                                       String areaCode,
                                                       String scenicId,
                                                       String areaName) {
        if (isAnonymousUser(userId)) {
            log.info("[UserProfileCache] skip anonymous userId={}, enablePersonalization=false", userId);
            return null;
        }

        String cacheKey = buildCacheKey(userId, areaId, areaCode, scenicId, areaName);
        CacheEntry cached = profileCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAt <= PROFILE_CACHE_TTL_MS) {
            log.info("[UserProfileCache] hit key={}, profileVersion={}, fetchedAt={}",
                    cacheKey, cached.profileVersion, cached.fetchedAt);
            return copySnapshot(cached.snapshot);
        }

        Map<String, Object> remoteSnapshot = fetchRemoteSnapshot(userId, areaId, areaCode);
        if (remoteSnapshot != null && !remoteSnapshot.isEmpty()) {
            String remoteAreaCode = readText(remoteSnapshot, "areaCode", "area_code");
            String effectiveCacheKey = hasText(remoteAreaCode)
                    ? buildCacheKey(userId, areaId, remoteAreaCode, scenicId, areaName)
                    : cacheKey;
            CacheEntry effectiveCached = effectiveCacheKey.equals(cacheKey) ? cached : profileCache.get(effectiveCacheKey);
            if (!effectiveCacheKey.equals(cacheKey)) {
                log.info("[UserProfileCache] switch key from {} to {} by remote areaCode={}",
                        cacheKey, effectiveCacheKey, remoteAreaCode);
            }
            return updateCacheWithVersion(effectiveCacheKey, remoteSnapshot, effectiveCached, now);
        }

        if (cached != null) {
            log.warn("[UserProfileCache] remote unavailable, use stale cache key={}, profileVersion={}, fetchedAt={}",
                    cacheKey, cached.profileVersion, cached.fetchedAt);
            return copySnapshot(cached.snapshot);
        }

        log.info("[UserProfileCache] miss key={}, no local snapshot, enablePersonalization=false", cacheKey);
        return null;
    }

    private Map<String, Object> fetchRemoteSnapshot(String userId, Long areaId, String areaCode) {
        if (!hasText(userId) || areaId == null) {
            log.info("[UserProfileCache] skip remote fetch, userId or areaId missing: userId={}, areaId={}, areaCode={}",
                    userId, areaId, areaCode);
            return null;
        }

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(trimTrailingSlash(backendProperties.getBaseUrl()))
                    .path(ensureLeadingSlash(backendProperties.getProfileContextEndpoint()))
                    .queryParam("userId", userId)
                    .queryParam("areaId", areaId)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> rawResponse = restTemplate.getForObject(url, Map.class);
            Map<String, Object> data = unwrapProfileData(rawResponse);
            if (data == null || data.isEmpty()) {
                log.warn("[UserProfileCache] remote empty userId={}, areaId={}, areaCode={}", userId, areaId, areaCode);
                return null;
            }

            Map<String, Object> snapshot = buildAiProfileSnapshot(data, userId, areaId, areaCode);
            log.info("[UserProfileCache] remote fetched userId={}, areaId={}, areaCode={}, profileVersion={}, profileTags={}",
                    userId,
                    areaId,
                    firstNotBlank(readText(snapshot, "areaCode"), areaCode),
                    readVersion(snapshot.get("profileVersion")),
                    countList(snapshot.get("profileTags")));
            return snapshot;
        } catch (Exception e) {
            log.warn("[UserProfileCache] remote fetch failed userId={}, areaId={}, areaCode={}, error={}",
                    userId, areaId, areaCode, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapProfileData(Map<String, Object> rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return null;
        }
        Object data = rawResponse.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return objectMapper.convertValue(dataMap, new TypeReference<Map<String, Object>>() {
            });
        }
        return objectMapper.convertValue(rawResponse, new TypeReference<Map<String, Object>>() {
        });
    }

    private Map<String, Object> buildAiProfileSnapshot(Map<String, Object> data,
                                                       String fallbackUserId,
                                                       Long fallbackAreaId,
                                                       String fallbackAreaCode) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfHasText(snapshot, "userId", firstNotBlank(readText(data, "userId", "user_id"), fallbackUserId));
        Object areaIdValue = firstNonNull(readObject(data, "areaId", "area_id"), fallbackAreaId);
        if (areaIdValue != null) {
            snapshot.put("areaId", areaIdValue);
        }
        putIfHasText(snapshot, "areaCode", firstNotBlank(readText(data, "areaCode", "area_code"), fallbackAreaCode));
        Object profileVersion = readObject(data, "profileVersion", "profile_version");
        if (profileVersion != null) {
            snapshot.put("profileVersion", profileVersion);
        }
        Object profile = readObject(data, "profile");
        snapshot.put("profile", profile instanceof Map<?, ?> || profile instanceof List<?> ? profile : new LinkedHashMap<>());
        snapshot.put("profileTags", normalizeProfileTags(readObject(data, "profileTags", "profile_tags")));
        Object shortTermContext = readObject(data, "shortTermContext", "short_term_context");
        snapshot.put("shortTermContext", shortTermContext == null ? new LinkedHashMap<>() : shortTermContext);
        putIfHasText(snapshot, "updatedAt", readText(data, "updatedAt", "updated_at"));

        copyIfPresent(snapshot, data, "interestTags", "interest_tags");
        copyIfPresent(snapshot, data, "avoidTags", "avoid_tags");
        copyIfPresent(snapshot, data, "groupType", "group_type");
        copyIfPresent(snapshot, data, "physicalStrength", "physical_strength");
        return snapshot;
    }

    private List<Object> normalizeProfileTags(Object rawTags) {
        List<Object> normalized = new ArrayList<>();
        if (!(rawTags instanceof List<?> list)) {
            return normalized;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> tag = objectMapper.convertValue(rawMap, new TypeReference<Map<String, Object>>() {
                });
                normalizeProfileTag(tag);
                normalized.add(tag);
            } else if (item != null) {
                normalized.add(item);
            }
        }
        return normalized;
    }

    private void normalizeProfileTag(Map<String, Object> tag) {
        if (tag == null) {
            return;
        }
        Object dimension = firstNonNull(tag.get("dimension"), tag.get("dimensionCode"), tag.get("dimension_code"));
        if (dimension != null) {
            tag.putIfAbsent("dimension", dimension);
            tag.putIfAbsent("dimensionCode", dimension);
            tag.putIfAbsent("dimension_code", dimension);
        }

        Object tagCode = firstNonNull(tag.get("tagCode"), tag.get("tag_code"));
        if (tagCode != null) {
            tag.putIfAbsent("tagCode", tagCode);
            tag.putIfAbsent("tag_code", tagCode);
        }

        Object tagName = firstNonNull(tag.get("tagName"), tag.get("tag_name"));
        if (tagName != null) {
            tag.putIfAbsent("tagName", tagName);
            tag.putIfAbsent("tag_name", tagName);
        }

        Object weight = firstNonNull(tag.get("weight"), tag.get("score"));
        if (weight != null) {
            tag.putIfAbsent("weight", weight);
            tag.putIfAbsent("score", weight);
        }
    }

    private Map<String, Object> updateCacheWithVersion(String cacheKey,
                                                       Map<String, Object> remoteSnapshot,
                                                       CacheEntry cached,
                                                       long now) {
        Long remoteVersion = readVersion(remoteSnapshot.get("profileVersion"));
        if (cached == null) {
            profileCache.put(cacheKey, new CacheEntry(remoteSnapshot, remoteVersion, now));
            log.info("[UserProfileCache] put key={}, profileVersion={}, fetchedAt={}", cacheKey, remoteVersion, now);
            return copySnapshot(remoteSnapshot);
        }

        Long localVersion = cached.profileVersion;
        if (remoteVersion != null && localVersion != null) {
            if (remoteVersion > localVersion) {
                profileCache.put(cacheKey, new CacheEntry(remoteSnapshot, remoteVersion, now));
                log.info("[UserProfileCache] update key={}, oldVersion={}, newVersion={}, fetchedAt={}",
                        cacheKey, localVersion, remoteVersion, now);
                return copySnapshot(remoteSnapshot);
            }
            if (remoteVersion.equals(localVersion)) {
                cached.fetchedAt = now;
                log.info("[UserProfileCache] refresh key={}, profileVersion={}, fetchedAt={}",
                        cacheKey, localVersion, now);
                return copySnapshot(cached.snapshot);
            }
            log.warn("[UserProfileCache] keep newer local key={}, localVersion={}, remoteVersion={}",
                    cacheKey, localVersion, remoteVersion);
            return copySnapshot(cached.snapshot);
        }

        if (remoteVersion == null && localVersion != null) {
            cached.fetchedAt = now;
            log.warn("[UserProfileCache] remote missing profileVersion, keep local key={}, localVersion={}",
                    cacheKey, localVersion);
            return copySnapshot(cached.snapshot);
        }

        profileCache.put(cacheKey, new CacheEntry(remoteSnapshot, remoteVersion, now));
        log.info("[UserProfileCache] replace key={}, profileVersion={}, fetchedAt={}", cacheKey, remoteVersion, now);
        return copySnapshot(remoteSnapshot);
    }

    private Map<String, Object> copySnapshot(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        return objectMapper.convertValue(snapshot, new TypeReference<Map<String, Object>>() {
        });
    }

    private String buildCacheKey(String userId, Long areaId, String areaCode, String scenicId, String areaName) {
        String areaKey = firstNotBlank(
                areaCode,
                areaId == null ? "" : String.valueOf(areaId),
                scenicId,
                areaName,
                "GLOBAL"
        );
        return CACHE_PREFIX + userId.trim() + ":" + areaKey;
    }

    private boolean isAnonymousUser(String userId) {
        String value = userId == null ? "" : userId.trim().toLowerCase();
        return value.isEmpty()
                || "anonymous".equals(value)
                || value.startsWith("visitor_")
                || value.startsWith("android-live2d-");
    }

    private Long readVersion(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object readObject(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private String readText(Map<String, Object> map, String... keys) {
        Object value = readObject(map, keys);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void putIfHasText(Map<String, Object> map, String key, String value) {
        if (map != null && hasText(key) && hasText(value)) {
            map.put(key, value.trim());
        }
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String targetKey, String sourceKey) {
        Object value = readObject(source, targetKey, sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    private int countList(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
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

    private static class CacheEntry {
        private final Map<String, Object> snapshot;
        private final Long profileVersion;
        private volatile long fetchedAt;

        private CacheEntry(Map<String, Object> snapshot, Long profileVersion, long fetchedAt) {
            this.snapshot = snapshot;
            this.profileVersion = profileVersion;
            this.fetchedAt = fetchedAt;
        }
    }
}
