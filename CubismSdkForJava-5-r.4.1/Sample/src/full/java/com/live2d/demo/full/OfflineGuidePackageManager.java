/*
 * OfflineGuidePackageManager
 *
 * Reads and manages offline guide packages stored in assets.
 * Path: assets/offline_packages/area_{areaId}/manifest.json
 *
 * Looks up spots by marker_code or spot_id/scene_code.
 * Thread-safe for reads; loaded once and cached.
 */

package com.live2d.demo.full;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class OfflineGuidePackageManager {

    private static final String TAG = "OfflineGuidePackageManager";
    private static final String MANIFEST_PATH_TEMPLATE = "offline_packages/area_%d/manifest.json";

    private final Context context;
    private final Map<Integer, OfflineGuidePackage> packageCache = new HashMap<>();
    private boolean packagesLoaded = false;
    private boolean loadAttempted = false;

    public OfflineGuidePackageManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Look up an OfflineSpot by marker_code.
     * Returns null if not found or package not loaded.
     */
    public OfflineSpot findSpotByMarkerCode(String markerCode, int areaId) {
        ensurePackagesLoaded();

        OfflineGuidePackage pkg = packageCache.get(areaId);
        if (pkg == null) {
            Log.d(TAG, "No offline package for areaId=" + areaId);
            return null;
        }

        // Find the NFC marker
        OfflineNfcMarker marker = null;
        for (OfflineNfcMarker m : pkg.nfcMarkers) {
            if (markerCode.equals(m.markerCode)) {
                marker = m;
                break;
            }
        }

        if (marker == null) {
            Log.d(TAG, "marker_code " + markerCode + " not found in area " + areaId);
            return null;
        }

        // Find the spot by target_id
        if ("SPOT".equalsIgnoreCase(marker.targetType)) {
            for (OfflineSpot spot : pkg.spots) {
                if (isOfflineSpotMatch(spot, marker)) {
                    Log.d(TAG, "[NFC] Offline marker matched: " + markerCode + " -> " + spot.name
                            + " (spotId=" + spot.spotId + ")");
                    return spot;
                }
            }
        }

        Log.d(TAG, "Spot not found for targetId=" + marker.targetId + " in area " + areaId);
        return null;
    }

    private boolean isOfflineSpotMatch(OfflineSpot spot, OfflineNfcMarker marker) {
        if (spot == null || marker == null) {
            return false;
        }
        if (marker.targetId > 0 && spot.spotId == marker.targetId) {
            return true;
        }
        if (marker.sceneCode != null && marker.sceneCode.length() > 0
                && marker.sceneCode.equals(spot.sceneCode)) {
            return true;
        }
        return marker.targetName != null && marker.targetName.length() > 0
                && marker.targetName.equals(spot.name);
    }

    /**
     * Look up an OfflineSpot by spot_id.
     */
    public OfflineSpot findSpotById(int spotId, int areaId) {
        ensurePackagesLoaded();

        OfflineGuidePackage pkg = packageCache.get(areaId);
        if (pkg == null) return null;

        for (OfflineSpot spot : pkg.spots) {
            if (spot.spotId == spotId) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Look up an OfflineSpot by scene_code.
     */
    public OfflineSpot findSpotBySceneCode(String sceneCode, int areaId) {
        ensurePackagesLoaded();

        OfflineGuidePackage pkg = packageCache.get(areaId);
        if (pkg == null) return null;

        for (OfflineSpot spot : pkg.spots) {
            if (sceneCode.equals(spot.sceneCode)) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Get the full guide package for an area.
     */
    public OfflineGuidePackage getPackage(int areaId) {
        ensurePackagesLoaded();
        return packageCache.get(areaId);
    }

    /**
     * 检查某个 marker_code 是否在离线包中存在 (不返回spot，只判断存在性).
     */
    public boolean hasMarkerCode(String markerCode, int areaId) {
        return findSpotByMarkerCode(markerCode, areaId) != null;
    }

    /**
     * Check if any packages were loaded successfully.
     */
    public boolean hasAnyPackage() {
        ensurePackagesLoaded();
        return !packageCache.isEmpty();
    }

    private void ensurePackagesLoaded() {
        if (packagesLoaded || loadAttempted) return;
        loadAttempted = true;

        // Try to load packages for known area IDs.
        // Currently only area_1 is supported; the structure allows expansion.
        int[] areaIdsToTry = {1};
        for (int areaId : areaIdsToTry) {
            try {
                OfflineGuidePackage pkg = loadPackage(areaId);
                if (pkg != null) {
                    packageCache.put(areaId, pkg);
                    Log.d(TAG, "[NFC] Offline package loaded: area_" + areaId
                            + " (" + pkg.areaName + ", " + pkg.spots.size() + " spots, "
                            + pkg.nfcMarkers.size() + " NFC markers)");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load offline package for area " + areaId, e);
            }
        }
        packagesLoaded = true;
    }

    private OfflineGuidePackage loadPackage(int areaId) {
        String path = String.format(MANIFEST_PATH_TEMPLATE, areaId);
        File localManifest = new File(context.getFilesDir(), path);
        OfflineGuidePackage localPackage = loadPackageFromLocalFile(localManifest);
        if (localPackage != null) {
            return localPackage;
        }

        Log.d(TAG, "[OfflinePackage] local manifest missing, try assets path="
                + localManifest.getAbsolutePath());
        return loadPackageFromAssets(path);
    }

    public OfflineGuidePackage reloadPackage(int areaId) {
        OfflineGuidePackage pkg = loadPackage(areaId);
        if (pkg != null) {
            packageCache.put(areaId, pkg);
            packagesLoaded = true;
            loadAttempted = true;
            Log.d(TAG, "[OfflinePackage] reload package success area=" + areaId);
        }
        return pkg;
    }

    private OfflineGuidePackage loadPackageFromLocalFile(File manifestFile) {
        if (manifestFile == null || !manifestFile.exists() || !manifestFile.isFile()) {
            return null;
        }

        Log.d(TAG, "[OfflinePackage] load local manifest: " + manifestFile.getAbsolutePath());
        try {
            InputStream is = new FileInputStream(manifestFile);
            OfflineGuidePackage pkg = readPackageFromStream(is, getAreaIdFromManifestPath(manifestFile));
            if (pkg != null) {
                Log.d(TAG, "[OfflinePackage] local manifest loaded: area=" + pkg.areaId
                        + ", version=" + pkg.version
                        + ", markers=" + pkg.nfcMarkers.size());
            }
            return pkg;
        } catch (Exception e) {
            Log.w(TAG, "[OfflinePackage] load local manifest failed: "
                    + manifestFile.getAbsolutePath(), e);
            return null;
        }
    }

    private OfflineGuidePackage loadPackageFromAssets(String path) {
        try {
            InputStream is = context.getAssets().open(path);
            OfflineGuidePackage pkg = readPackageFromStream(is, getAreaIdFromAssetPath(path));
            if (pkg != null) {
                Log.d(TAG, "[OfflinePackage] assets manifest loaded: area=" + pkg.areaId
                        + ", version=" + pkg.version
                        + ", markers=" + pkg.nfcMarkers.size());
            }
            return pkg;
        } catch (Exception e) {
            Log.w(TAG, "Could not load manifest from assets: " + path, e);
            return null;
        }
    }

    private OfflineGuidePackage readPackageFromStream(InputStream is, int fallbackAreaId) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            try { reader.close(); } catch (Exception ignored) {}
            try { is.close(); } catch (Exception ignored) {}
        }

        JSONObject json = new JSONObject(sb.toString());
        normalizeManifestJson(json, fallbackAreaId);
        return OfflineGuidePackage.fromJson(json);
    }

    private void normalizeManifestJson(JSONObject json, int fallbackAreaId) {
        if (json == null) {
            return;
        }

        String areaIdText = firstJsonText(json, "area_id", "areaId", "scenic_id", "scenicId", "park_id", "parkId");
        if (parsePositiveInt(areaIdText, 0) <= 0 && fallbackAreaId > 0) {
            areaIdText = String.valueOf(fallbackAreaId);
        }
        if (parsePositiveInt(areaIdText, 0) > 0) {
            putOrReplace(json, "area_id", areaIdText);
        }
        putIfMissing(json, "version", firstJsonText(json, "package_version", "packageVersion", "version"));
        if (!json.has("nfc_markers") && json.has("nfcMarkers")) {
            try {
                json.put("nfc_markers", json.optJSONArray("nfcMarkers"));
            } catch (Exception ignored) {}
        }

        JSONArray spots = json.optJSONArray("spots");
        if (spots != null) {
            for (int i = 0; i < spots.length(); i++) {
                JSONObject spot = spots.optJSONObject(i);
                if (spot == null) continue;
                putIfMissing(spot, "spot_id", firstJsonText(
                        spot,
                        "spot_id",
                        "spotId",
                        "target_id",
                        "targetId",
                        "id"
                ));
                putIfMissing(spot, "name", firstJsonText(
                        spot,
                        "name",
                        "spot_name",
                        "spotName",
                        "target_name",
                        "targetName",
                        "guide_title",
                        "guideTitle",
                        "title"
                ));
                putIfMissing(spot, "short_intro", firstJsonText(
                        spot,
                        "short_intro",
                        "shortIntro",
                        "guide_summary",
                        "guideSummary",
                        "summary"
                ));
                putIfMissing(spot, "guide_text", firstJsonText(
                        spot,
                        "guide_text",
                        "guideText",
                        "description",
                        "intro",
                        "guide_summary",
                        "guideSummary",
                        "summary"
                ));
            }
        }

        JSONArray markers = json.optJSONArray("nfc_markers");
        if (markers != null) {
            for (int i = 0; i < markers.length(); i++) {
                JSONObject marker = markers.optJSONObject(i);
                if (marker == null) continue;
                putIfMissing(marker, "marker_code", firstJsonText(marker, "marker_code", "markerCode"));
                putIfMissing(marker, "marker_code", firstJsonText(marker, "code", "nfc_code", "nfcCode"));
                putIfMissing(marker, "target_type", firstJsonText(marker, "target_type", "targetType"));
                putIfMissing(marker, "target_id", firstJsonText(marker, "target_id", "targetId", "spot_id", "spotId", "id"));
                putIfMissing(marker, "target_name", firstJsonText(marker, "target_name", "targetName", "spot_name", "spotName", "name"));
            }
        }
    }

    private void putIfMissing(JSONObject json, String key, String value) {
        if (json == null || key == null || value == null || value.length() == 0) {
            return;
        }
        if (json.has(key) && json.optString(key, "").trim().length() > 0) {
            return;
        }
        try {
            json.put(key, value);
        } catch (Exception ignored) {}
    }

    private void putOrReplace(JSONObject json, String key, String value) {
        if (json == null || key == null || value == null || value.length() == 0) {
            return;
        }
        try {
            json.put(key, value);
        } catch (Exception ignored) {}
    }

    private int getAreaIdFromManifestPath(File manifestFile) {
        if (manifestFile == null) {
            return 0;
        }
        return parseAreaIdFromText(manifestFile.getAbsolutePath());
    }

    private int getAreaIdFromAssetPath(String path) {
        return parseAreaIdFromText(path);
    }

    private int parseAreaIdFromText(String text) {
        if (text == null) {
            return 0;
        }
        String marker = "area_";
        int index = text.indexOf(marker);
        while (index >= 0) {
            int start = index + marker.length();
            StringBuilder builder = new StringBuilder();
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= '0' && c <= '9') {
                    builder.append(c);
                } else {
                    break;
                }
            }
            int parsed = parsePositiveInt(builder.toString(), 0);
            if (parsed > 0) {
                return parsed;
            }
            index = text.indexOf(marker, start);
        }
        return 0;
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String firstJsonText(JSONObject json, String... keys) {
        if (json == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = json.optString(key, "");
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * Get the local audio asset path for a spot, if available.
     * Returns the path relative to assets, e.g. "offline_packages/area_1/audio/LS-013.mp3"
     */
    public String getLocalAudioPath(OfflineSpot spot, int areaId) {
        if (spot == null || spot.localAudio == null || spot.localAudio.isEmpty()) {
            return null;
        }
        // Construct full path: offline_packages/area_{areaId}/{localAudio}
        String basePath = String.format("offline_packages/area_%d/", areaId);
        String audioPath = spot.localAudio;
        if (audioPath.startsWith("/")) {
            audioPath = audioPath.substring(1);
        }
        return basePath + audioPath;
    }
}
