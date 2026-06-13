package com.live2d.demo.full;

import org.json.JSONObject;

class OfflinePackageInfo {
    public String areaId = "";
    public String areaName = "";
    public String packageVersion = "";
    public String packageUrl = "";
    public long packageSize = 0L;
    public String contentHash = "";
    public int includesAudio = 0;
    public int includesMap = 0;
    public int spotCount = 0;
    public int nfcMarkerCount = 0;
    public int routeCount = 0;
    public int faqCount = 0;
    public String publishedAt = "";

    static OfflinePackageInfo fromJson(JSONObject json) {
        if (json == null) return null;

        OfflinePackageInfo info = new OfflinePackageInfo();
        info.areaId = optString(json, "areaId", "area_id");
        info.areaName = optString(json, "areaName", "area_name");
        info.packageVersion = optString(json, "packageVersion", "package_version", "version");
        info.packageUrl = optString(json, "packageUrl", "package_url");
        info.packageSize = optLong(json, "packageSize", "package_size");
        info.contentHash = optString(json, "contentHash", "content_hash");
        info.includesAudio = optInt(json, "includesAudio", "includes_audio");
        info.includesMap = optInt(json, "includesMap", "includes_map");
        info.spotCount = optInt(json, "spotCount", "spot_count");
        info.nfcMarkerCount = optInt(json, "nfcMarkerCount", "nfc_marker_count");
        info.routeCount = optInt(json, "routeCount", "route_count");
        info.faqCount = optInt(json, "faqCount", "faq_count");
        info.publishedAt = optString(json, "publishedAt", "published_at");
        return info;
    }

    JSONObject toMetaJson(String downloadedAt, String resolvedPackageUrl, String packageType) {
        JSONObject json = new JSONObject();
        try {
            json.put("areaId", areaId);
            json.put("packageVersion", packageVersion);
            json.put("packageUrl", packageUrl);
            json.put("resolvedPackageUrl", resolvedPackageUrl);
            json.put("packageType", packageType);
            json.put("contentHash", contentHash);
            json.put("downloadedAt", downloadedAt);
        } catch (Exception ignored) {}
        return json;
    }

    private static String optString(JSONObject json, String... keys) {
        if (json == null || keys == null) return "";
        for (String key : keys) {
            String value = json.optString(key, "");
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private static int optInt(JSONObject json, String... keys) {
        if (json == null || keys == null) return 0;
        for (String key : keys) {
            if (json.has(key)) {
                return json.optInt(key, 0);
            }
        }
        return 0;
    }

    private static long optLong(JSONObject json, String... keys) {
        if (json == null || keys == null) return 0L;
        for (String key : keys) {
            if (json.has(key)) {
                return json.optLong(key, 0L);
            }
        }
        return 0L;
    }
}
