/*
 * OfflineBehaviorQueue
 *
 * Local event queue using SharedPreferences + JSON.
 * Saves NFC_CHECKIN events when offline/weak network.
 * Supports add, list, markSynced operations.
 *
 * Does NOT introduce a complex database (Room/SQLite).
 */

package com.live2d.demo.full;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class OfflineBehaviorQueue {

    private static final String TAG = "OfflineBehaviorQueue";
    private static final String PREF_NAME = "nfc_offline_behavior_queue";
    private static final String KEY_EVENTS = "pending_events";

    private final SharedPreferences prefs;

    public OfflineBehaviorQueue(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Add a pending NFC_CHECKIN event to the queue.
     */
    public String addPendingEvent(String userId, String visitId, Long areaId,
                                   Long spotId, String sceneCode, String spotName,
                                   String markerCode) {
        String eventId = generateEventId();
        String eventTime = getCurrentIsoTime();

        BehaviorEventItem item = new BehaviorEventItem();
        item.eventId = eventId;
        item.userId = userId;
        item.visitId = visitId;
        item.areaId = areaId;
        item.spotId = spotId;
        item.sceneCode = sceneCode;
        item.markerCode = markerCode != null ? markerCode : "";
        item.eventType = "NFC_CHECKIN";
        item.eventName = "NFC打点进入" + (spotName != null ? spotName : sceneCode);
        item.eventTime = eventTime;

        List<BehaviorEventItem> events = listPendingEvents();
        events.add(item);
        saveEvents(events);

        Log.d(TAG, "Added pending event: " + eventId + " type=NFC_CHECKIN spot=" + spotName
                + " marker=" + markerCode);
        return eventId;
    }

    /**
     * List all PENDING events in the queue.
     */
    public List<BehaviorEventItem> listPendingEvents() {
        List<BehaviorEventItem> events = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_EVENTS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                BehaviorEventItem item = new BehaviorEventItem();
                item.eventId = obj.optString("event_id", "");
                item.userId = obj.optString("user_id", "");
                item.visitId = obj.optString("visit_id", "");
                item.areaId = optLongOrNull(obj, "area_id");
                item.spotId = optLongOrNull(obj, "spot_id");
                item.sceneCode = obj.optString("scene_code", "");
                item.markerCode = obj.optString("marker_code", "");
                item.eventType = obj.optString("event_type", "NFC_CHECKIN");
                item.eventName = obj.optString("event_name", "");
                item.eventTime = obj.optString("event_time", "");
                events.add(item);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse pending events, resetting queue", e);
            prefs.edit().putString(KEY_EVENTS, "[]").apply();
        }
        return events;
    }

    /**
     * Remove successfully synced events from the queue.
     */
    public void removeEvents(List<String> syncedEventIds) {
        if (syncedEventIds == null || syncedEventIds.isEmpty()) return;

        List<BehaviorEventItem> all = listPendingEvents();
        List<BehaviorEventItem> remaining = new ArrayList<>();
        for (BehaviorEventItem item : all) {
            if (!syncedEventIds.contains(item.eventId)) {
                remaining.add(item);
            }
        }
        int removed = all.size() - remaining.size();
        if (removed > 0) {
            Log.d(TAG, "Removed " + removed + " synced events from queue");
        }
        saveEvents(remaining);
    }

    /**
     * Clear all pending events.
     */
    public void clearAll() {
        prefs.edit().putString(KEY_EVENTS, "[]").apply();
        Log.d(TAG, "Cleared all pending events");
    }

    /**
     * Get the count of pending events.
     */
    public int getPendingCount() {
        return listPendingEvents().size();
    }

    private void saveEvents(List<BehaviorEventItem> events) {
        try {
            JSONArray arr = new JSONArray();
            for (BehaviorEventItem item : events) {
                JSONObject obj = new JSONObject();
                obj.put("event_id", item.eventId);
                obj.put("user_id", item.userId);
                if (item.visitId != null && !item.visitId.isEmpty()) {
                    obj.put("visit_id", item.visitId);
                }
                if (item.areaId != null) obj.put("area_id", item.areaId);
                if (item.spotId != null) obj.put("spot_id", item.spotId);
                if (item.sceneCode != null && !item.sceneCode.isEmpty()) {
                    obj.put("scene_code", item.sceneCode);
                }
                if (item.markerCode != null && !item.markerCode.isEmpty()) {
                    obj.put("marker_code", item.markerCode);
                }
                obj.put("event_type", item.eventType);
                obj.put("event_name", item.eventName);
                obj.put("event_time", item.eventTime);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_EVENTS, arr.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save pending events", e);
        }
    }

    private String generateEventId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "evt_" + uuid.substring(0, 16);
    }

    private String getCurrentIsoTime() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.format(new Date());
        } catch (Exception e) {
            return "";
        }
    }

    private Long optLongOrNull(JSONObject json, String key) {
        if (json.has(key) && !json.isNull(key)) {
            try {
                return json.getLong(key);
            } catch (Exception e) {
                // Value may be a string
                try {
                    return Long.parseLong(json.optString(key, ""));
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
