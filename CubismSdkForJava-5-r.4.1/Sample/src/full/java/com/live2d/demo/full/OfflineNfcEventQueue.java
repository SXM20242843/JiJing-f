package com.live2d.demo.full;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OfflineNfcEventQueue {

    private static final String TAG = "OfflineNfcEventQueue";
    private static final String EVENT_TYPE_NFC_CHECKIN = "NFC_CHECKIN";
    private static final String STATUS_PENDING = "PENDING";
    private static final long DUPLICATE_WINDOW_MS = 60L * 1000L;
    private static final long EVENT_KEEP_MS = 7L * 24L * 60L * 60L * 1000L;

    private final File queueFile;

    public OfflineNfcEventQueue(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "offline_queue");
        this.queueFile = new File(dir, "nfc_pending_events.json");
    }

    public synchronized String enqueueOfflineNfcEvent(
            String userId,
            String visitId,
            int areaId,
            String markerCode,
            OfflineSpot spot,
            String offlineReason,
            String clientTime
    ) {
        Log.d(TAG, "[NFC][OfflineQueue] enqueue start markerCode=" + markerCode);
        cleanupOldEvents();

        long now = System.currentTimeMillis();
        List<OfflineNfcEvent> events = loadAllEvents();
        long spotId = spot != null ? spot.spotId : 0L;
        String normalizedVisitId = safeString(visitId);
        String normalizedMarkerCode = safeString(markerCode);

        for (int i = 0; i < events.size(); i++) {
            OfflineNfcEvent event = events.get(i);
            if (STATUS_PENDING.equals(event.syncStatus)
                    && normalizedVisitId.equals(event.visitId)
                    && normalizedMarkerCode.equals(event.markerCode)
                    && spotId == event.spotId
                    && now - event.createdAt >= 0L
                    && now - event.createdAt < DUPLICATE_WINDOW_MS) {
                Log.d(TAG, "[NFC][OfflineQueue] duplicate skip markerCode=" + normalizedMarkerCode);
                return "";
            }
        }

        OfflineNfcEvent event = new OfflineNfcEvent();
        event.localId = generateLocalId(now, events.size() + 1);
        event.eventType = EVENT_TYPE_NFC_CHECKIN;
        event.syncStatus = STATUS_PENDING;
        event.userId = safeString(userId);
        event.visitId = normalizedVisitId;
        event.areaId = areaId;
        event.markerCode = normalizedMarkerCode;
        event.targetType = "SPOT";
        event.spotId = spotId;
        event.spotName = spot != null ? safeString(spot.name) : "";
        event.sceneCode = spot != null ? safeString(spot.sceneCode) : "";
        event.clientTime = safeString(clientTime);
        event.offlineReason = safeString(offlineReason);
        event.createdAt = now;
        event.retryCount = 0;
        event.lastError = "";

        events.add(event);
        saveEvents(events);
        Log.d(TAG, "[NFC][OfflineQueue] enqueue success localId=" + event.localId
                + ", pendingCount=" + size());
        return event.localId;
    }

    public synchronized List<OfflineNfcEvent> loadPendingEvents() {
        List<OfflineNfcEvent> all = loadAllEvents();
        List<OfflineNfcEvent> pending = new ArrayList<OfflineNfcEvent>();
        for (int i = 0; i < all.size(); i++) {
            OfflineNfcEvent event = all.get(i);
            if (STATUS_PENDING.equals(event.syncStatus)) {
                pending.add(event);
            }
        }
        return pending;
    }

    public synchronized void markSynced(String localId) {
        if (localId == null || localId.length() == 0) {
            return;
        }
        List<OfflineNfcEvent> all = loadAllEvents();
        List<OfflineNfcEvent> remaining = new ArrayList<OfflineNfcEvent>();
        for (int i = 0; i < all.size(); i++) {
            OfflineNfcEvent event = all.get(i);
            if (!localId.equals(event.localId)) {
                remaining.add(event);
            }
        }
        saveEvents(remaining);
    }

    public synchronized void markFailed(String localId, String error) {
        if (localId == null || localId.length() == 0) {
            return;
        }
        List<OfflineNfcEvent> all = loadAllEvents();
        for (int i = 0; i < all.size(); i++) {
            OfflineNfcEvent event = all.get(i);
            if (localId.equals(event.localId)) {
                event.retryCount = event.retryCount + 1;
                event.lastError = trimError(error);
                if (event.retryCount >= 5) {
                    Log.d(TAG, "[NFC][OfflineSync] retry limit reached localId=" + event.localId);
                }
                break;
            }
        }
        saveEvents(all);
    }

    public synchronized void cleanupOldEvents() {
        long now = System.currentTimeMillis();
        List<OfflineNfcEvent> all = loadAllEvents();
        List<OfflineNfcEvent> kept = new ArrayList<OfflineNfcEvent>();
        for (int i = 0; i < all.size(); i++) {
            OfflineNfcEvent event = all.get(i);
            if (event.createdAt <= 0L || now - event.createdAt < EVENT_KEEP_MS) {
                kept.add(event);
            }
        }
        if (kept.size() != all.size()) {
            saveEvents(kept);
        }
    }

    public synchronized int size() {
        return loadPendingEvents().size();
    }

    private List<OfflineNfcEvent> loadAllEvents() {
        List<OfflineNfcEvent> events = new ArrayList<OfflineNfcEvent>();
        if (!queueFile.exists()) {
            return events;
        }

        try {
            String text = readFile(queueFile);
            JSONArray array = new JSONArray(text.length() == 0 ? "[]" : text);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    events.add(OfflineNfcEvent.fromJson(obj));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "[NFC][OfflineQueue] read failed, reset queue", e);
            saveEvents(events);
        }
        return events;
    }

    private void saveEvents(List<OfflineNfcEvent> events) {
        try {
            File dir = queueFile.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }

            JSONArray array = new JSONArray();
            if (events != null) {
                for (int i = 0; i < events.size(); i++) {
                    array.put(events.get(i).toJson());
                }
            }
            writeFile(queueFile, array.toString());
        } catch (Exception e) {
            Log.w(TAG, "[NFC][OfflineQueue] save failed", e);
        }
    }

    private String readFile(File file) throws Exception {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file),
                    StandardCharsets.UTF_8
            ));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void writeFile(File file, String text) throws Exception {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file),
                    StandardCharsets.UTF_8
            ));
            writer.write(text == null ? "[]" : text);
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Exception ignored) {}
            }
        }
    }

    private String generateLocalId(long now, int index) {
        return "nfc_" + now + "_" + String.format(Locale.US, "%03d", index);
    }

    private String trimError(String error) {
        String text = safeString(error);
        if (text.length() > 160) {
            return text.substring(0, 160);
        }
        return text;
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    public static class OfflineNfcEvent {
        public String localId = "";
        public String eventType = EVENT_TYPE_NFC_CHECKIN;
        public String syncStatus = STATUS_PENDING;
        public String userId = "";
        public String visitId = "";
        public int areaId = 0;
        public String markerCode = "";
        public String targetType = "SPOT";
        public long spotId = 0L;
        public String spotName = "";
        public String sceneCode = "";
        public String clientTime = "";
        public String offlineReason = "";
        public long createdAt = 0L;
        public int retryCount = 0;
        public String lastError = "";

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("localId", localId);
                obj.put("eventType", eventType);
                obj.put("syncStatus", syncStatus);
                obj.put("userId", userId);
                obj.put("visitId", visitId);
                obj.put("areaId", areaId);
                obj.put("markerCode", markerCode);
                obj.put("targetType", targetType);
                obj.put("spotId", spotId);
                obj.put("spotName", spotName);
                obj.put("sceneCode", sceneCode);
                obj.put("clientTime", clientTime);
                obj.put("offlineReason", offlineReason);
                obj.put("createdAt", createdAt);
                obj.put("retryCount", retryCount);
                obj.put("lastError", lastError);
            } catch (Exception ignored) {}
            return obj;
        }

        public static OfflineNfcEvent fromJson(JSONObject obj) {
            OfflineNfcEvent event = new OfflineNfcEvent();
            event.localId = obj.optString("localId", "");
            event.eventType = obj.optString("eventType", EVENT_TYPE_NFC_CHECKIN);
            event.syncStatus = obj.optString("syncStatus", STATUS_PENDING);
            event.userId = obj.optString("userId", "");
            event.visitId = obj.optString("visitId", "");
            event.areaId = obj.optInt("areaId", 0);
            event.markerCode = obj.optString("markerCode", "");
            event.targetType = obj.optString("targetType", "SPOT");
            event.spotId = obj.optLong("spotId", 0L);
            event.spotName = obj.optString("spotName", "");
            event.sceneCode = obj.optString("sceneCode", "");
            event.clientTime = obj.optString("clientTime", "");
            event.offlineReason = obj.optString("offlineReason", "");
            event.createdAt = obj.optLong("createdAt", 0L);
            event.retryCount = obj.optInt("retryCount", 0);
            event.lastError = obj.optString("lastError", "");
            return event;
        }
    }
}
