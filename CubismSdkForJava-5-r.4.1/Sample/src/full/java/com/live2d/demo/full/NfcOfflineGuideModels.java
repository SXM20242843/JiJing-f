/*
 * NFC / Offline Guide Models
 *
 * Consolidated DTOs for NFC checkin, offline package, and behavior sync.
 * All fields use public access to match the existing MainActivity coding style.
 */

package com.live2d.demo.full;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single offline package (one area's offline data).
 */
class OfflineGuidePackage {
    public int areaId;
    public String areaName = "";
    public String version = "";
    public OfflineMapInfo map;
    public List<OfflineSpot> spots = new ArrayList<>();
    public List<OfflineNfcMarker> nfcMarkers = new ArrayList<>();
    public List<OfflineRoute> routes = new ArrayList<>();
    public List<OfflineFaq> faqs = new ArrayList<>();

    static OfflineGuidePackage fromJson(JSONObject json) {
        if (json == null) return null;
        OfflineGuidePackage pkg = new OfflineGuidePackage();
        pkg.areaId = json.optInt("area_id", json.optInt("areaId", 0));
        pkg.areaName = firstOptString(json, "area_name", "areaName");
        pkg.version = json.optString("version", "");

        // map
        JSONObject mapObj = json.optJSONObject("map");
        if (mapObj != null) {
            pkg.map = OfflineMapInfo.fromJson(mapObj);
        }

        // spots
        JSONArray spotsArr = json.optJSONArray("spots");
        if (spotsArr != null) {
            for (int i = 0; i < spotsArr.length(); i++) {
                JSONObject spotObj = spotsArr.optJSONObject(i);
                if (spotObj != null) {
                    pkg.spots.add(OfflineSpot.fromJson(spotObj));
                }
            }
        }

        // nfc_markers
        JSONArray markersArr = json.optJSONArray("nfc_markers");
        if (markersArr != null) {
            for (int i = 0; i < markersArr.length(); i++) {
                JSONObject markerObj = markersArr.optJSONObject(i);
                if (markerObj != null) {
                    pkg.nfcMarkers.add(OfflineNfcMarker.fromJson(markerObj));
                }
            }
        }

        // routes
        JSONArray routesArr = json.optJSONArray("routes");
        if (routesArr != null) {
            for (int i = 0; i < routesArr.length(); i++) {
                JSONObject routeObj = routesArr.optJSONObject(i);
                if (routeObj != null) {
                    pkg.routes.add(OfflineRoute.fromJson(routeObj));
                }
            }
        }

        // faqs
        JSONArray faqsArr = json.optJSONArray("faqs");
        if (faqsArr != null) {
            for (int i = 0; i < faqsArr.length(); i++) {
                JSONObject faqObj = faqsArr.optJSONObject(i);
                if (faqObj != null) {
                    pkg.faqs.add(OfflineFaq.fromJson(faqObj));
                }
            }
        }

        return pkg;
    }

    private static String firstOptString(JSONObject json, String... keys) {
        for (String key : keys) {
            String val = json.optString(key, "").trim();
            if (!val.isEmpty()) return val;
        }
        return "";
    }
}

class OfflineMapInfo {
    public String type = "IMAGE";
    public String localPath = "";
    public int width;
    public int height;

    static OfflineMapInfo fromJson(JSONObject json) {
        OfflineMapInfo info = new OfflineMapInfo();
        info.type = json.optString("type", "IMAGE");
        info.localPath = json.optString("local_path", json.optString("localPath", ""));
        info.width = json.optInt("width", 0);
        info.height = json.optInt("height", 0);
        return info;
    }
}

class OfflineSpot {
    public int spotId;
    public String sceneCode = "";
    public String name = "";
    public int x;
    public int y;
    public String shortIntro = "";
    public String guideText = "";
    public String localAudio = "";

    static OfflineSpot fromJson(JSONObject json) {
        OfflineSpot spot = new OfflineSpot();
        spot.spotId = json.optInt("spot_id", json.optInt("spotId", 0));
        spot.sceneCode = json.optString("scene_code", json.optString("sceneCode", ""));
        spot.name = json.optString("name", "");
        spot.x = json.optInt("x", 0);
        spot.y = json.optInt("y", 0);
        spot.shortIntro = json.optString("short_intro", json.optString("shortIntro", ""));
        spot.guideText = json.optString("guide_text", json.optString("guideText", ""));
        spot.localAudio = json.optString("local_audio", json.optString("localAudio", ""));
        return spot;
    }
}

class OfflineNfcMarker {
    public String markerCode = "";
    public String targetType = "SPOT";
    public int targetId;
    public String sceneCode = "";
    public String targetName = "";

    static OfflineNfcMarker fromJson(JSONObject json) {
        OfflineNfcMarker marker = new OfflineNfcMarker();
        marker.markerCode = json.optString("marker_code", json.optString("markerCode", ""));
        marker.targetType = json.optString("target_type", json.optString("targetType", "SPOT"));
        marker.targetId = json.optInt("target_id", json.optInt("targetId", 0));
        marker.sceneCode = json.optString("scene_code", json.optString("sceneCode", ""));
        marker.targetName = json.optString("target_name", json.optString("targetName", ""));
        return marker;
    }
}

class OfflineRoute {
    public String routeId = "";
    public String routeName = "";
    public List<Integer> spotIds = new ArrayList<>();
    public String description = "";

    static OfflineRoute fromJson(JSONObject json) {
        OfflineRoute route = new OfflineRoute();
        route.routeId = json.optString("route_id", json.optString("routeId", ""));
        route.routeName = json.optString("route_name", json.optString("routeName", ""));
        route.description = json.optString("description", "");
        JSONArray ids = json.optJSONArray("spot_ids");
        if (ids != null) {
            for (int i = 0; i < ids.length(); i++) {
                route.spotIds.add(ids.optInt(i, 0));
            }
        }
        return route;
    }
}

class OfflineFaq {
    public String question = "";
    public String answer = "";

    static OfflineFaq fromJson(JSONObject json) {
        OfflineFaq faq = new OfflineFaq();
        faq.question = json.optString("question", "");
        faq.answer = json.optString("answer", "");
        return faq;
    }
}

// ==================== Network State ====================

enum NetworkLevel {
    NORMAL,
    WEAK,
    OFFLINE
}

// ==================== NFC Checkin DTOs ====================

class NfcCheckinRequest {
    public String userId = "";
    public String visitId = "";
    public Long areaId;
    public String markerCode = "";
    public String clientTime = "";
    public String networkStatus = "NORMAL";
}

class NfcCheckinResponseData {
    public Long areaId;
    public String areaName = "";
    public String targetType = "SPOT";
    public Long spotId;
    public Long facilityId;
    public String sceneCode = "";
    public String spotName = "";
    public String targetName = "";
    public String markerCode = "";
    public String markerName = "";
    public double confidence = 0.98;
    public String confidenceLevel = "HIGH";
    public String locationSource = "NFC";
    public String triggerAction = "GUIDE";
    public NfcCheckinGuide guide;

    static NfcCheckinResponseData fromJson(JSONObject json) {
        if (json == null) return null;
        NfcCheckinResponseData data = new NfcCheckinResponseData();
        data.areaId = optLong(json, "area_id", "areaId");
        data.areaName = json.optString("area_name", json.optString("areaName", ""));
        data.targetType = json.optString("target_type", json.optString("targetType", "SPOT"));
        data.spotId = optLong(json, "spot_id", "spotId");
        data.facilityId = optLong(json, "facility_id", "facilityId");
        data.sceneCode = json.optString("scene_code", json.optString("sceneCode", ""));
        data.spotName = json.optString("spot_name", json.optString("spotName", ""));
        data.targetName = json.optString("target_name", json.optString("targetName", ""));
        data.markerCode = json.optString("marker_code", json.optString("markerCode", ""));
        data.markerName = json.optString("marker_name", json.optString("markerName", ""));
        data.confidence = json.optDouble("confidence", 0.98);
        data.confidenceLevel = json.optString("confidence_level", json.optString("confidenceLevel", "HIGH"));
        data.locationSource = json.optString("location_source", json.optString("locationSource", "NFC"));
        data.triggerAction = json.optString("trigger_action", json.optString("triggerAction", "GUIDE"));

        JSONObject guideObj = json.optJSONObject("guide");
        if (guideObj != null) {
            data.guide = NfcCheckinGuide.fromJson(guideObj);
        }
        return data;
    }

    private static Long optLong(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) {
                long val = json.optLong(key, -1);
                if (val >= 0) return val;
            }
        }
        return null;
    }
}

class NfcCheckinGuide {
    public String title = "";
    public String summary = "";

    static NfcCheckinGuide fromJson(JSONObject json) {
        NfcCheckinGuide guide = new NfcCheckinGuide();
        guide.title = json.optString("title", "");
        guide.summary = json.optString("summary", "");
        return guide;
    }
}

// ==================== Behavior Sync DTOs ====================

class BehaviorEventItem {
    public String eventId = "";
    public String userId = "";
    public String visitId = "";
    public Long areaId;
    public Long spotId;
    public String sceneCode = "";
    public String markerCode = "";
    public String eventType = "NFC_CHECKIN";
    public String eventName = "";
    public String eventTime = "";

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("event_id", eventId);
            json.put("user_id", userId);
            if (visitId != null && !visitId.isEmpty()) json.put("visit_id", visitId);
            if (areaId != null) json.put("area_id", areaId);
            if (spotId != null) json.put("spot_id", spotId);
            if (sceneCode != null && !sceneCode.isEmpty()) json.put("scene_code", sceneCode);
            if (markerCode != null && !markerCode.isEmpty()) json.put("marker_code", markerCode);
            json.put("event_type", eventType);
            json.put("event_name", eventName);
            json.put("event_time", eventTime);
        } catch (Exception ignored) {}
        return json;
    }
}

class BehaviorBatchSyncRequest {
    public String userId = "";
    public List<BehaviorEventItem> events = new ArrayList<>();

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("user_id", userId);
            JSONArray arr = new JSONArray();
            for (BehaviorEventItem item : events) {
                arr.put(item.toJson());
            }
            json.put("events", arr);
        } catch (Exception ignored) {}
        return json;
    }
}

class BehaviorBatchSyncResponseData {
    public int received;
    public int success;
    public int failed;

    static BehaviorBatchSyncResponseData fromJson(JSONObject json) {
        BehaviorBatchSyncResponseData data = new BehaviorBatchSyncResponseData();
        data.received = json.optInt("received", 0);
        data.success = json.optInt("success", 0);
        data.failed = json.optInt("failed", 0);
        return data;
    }
}

// ==================== Location / Network Context (for AI chat forwarding) ====================

class LocationContext {
    public String source = "NFC";
    public double confidence = 0.98;
    public String confidenceLevel = "HIGH";
    public Long areaId;
    public String areaName = "";
    public Long currentSpotId;
    public String currentSpotName = "";
    public String sceneCode = "";

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("source", source);
            json.put("confidence", confidence);
            json.put("confidence_level", confidenceLevel);
            if (areaId != null) json.put("area_id", areaId);
            json.put("area_name", areaName);
            if (currentSpotId != null) json.put("current_spot_id", currentSpotId);
            json.put("current_spot_name", currentSpotName);
            json.put("scene_code", sceneCode);
        } catch (Exception ignored) {}
        return json;
    }
}

class NetworkContext {
    public String networkLevel = "NORMAL";
    public boolean preferTextFirst = false;
    public boolean ttsAsync = false;
    public boolean allowOfflineFallback = true;

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("network_level", networkLevel);
            json.put("prefer_text_first", preferTextFirst);
            json.put("tts_async", ttsAsync);
            json.put("allow_offline_fallback", allowOfflineFallback);
        } catch (Exception ignored) {}
        return json;
    }
}
