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

import org.json.JSONObject;

import java.io.BufferedReader;
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
                if (spot.spotId == marker.targetId) {
                    Log.d(TAG, "[NFC] Offline marker matched: " + markerCode + " -> " + spot.name
                            + " (spotId=" + spot.spotId + ")");
                    return spot;
                }
            }
        }

        Log.d(TAG, "Spot not found for targetId=" + marker.targetId + " in area " + areaId);
        return null;
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
        try {
            InputStream is = context.getAssets().open(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            is.close();

            JSONObject json = new JSONObject(sb.toString());
            return OfflineGuidePackage.fromJson(json);
        } catch (Exception e) {
            Log.w(TAG, "Could not load manifest from assets: " + path, e);
            return null;
        }
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
