/*
 * BehaviorSyncClient
 *
 * HTTP client for batch-syncing offline behavior events.
 * POST /api/app/behavior/batch-sync
 *
 * Runs on background thread. Never throws exceptions to the UI.
 */

package com.live2d.demo.full;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class BehaviorSyncClient {

    private static final String TAG = "BehaviorSyncClient";

    private final String backendBaseUrl;
    private final String authToken;

    public BehaviorSyncClient(String backendBaseUrl, String authToken) {
        this.backendBaseUrl = backendBaseUrl != null ? trimTrailingSlash(backendBaseUrl) : "";
        this.authToken = authToken != null ? authToken : "";
    }

    /**
     * Call POST /api/app/behavior/batch-sync.
     * Returns the response data or null on failure.
     */
    public BehaviorBatchSyncResponseData batchSync(BehaviorBatchSyncRequest request) {
        if (request == null || request.events == null || request.events.isEmpty()) {
            Log.d(TAG, "No events to sync, skipping");
            return null;
        }

        try {
            String url = backendBaseUrl + "/api/app/behavior/batch-sync";
            Log.d(TAG, "Syncing " + request.events.size() + " events to: " + url);

            JSONObject body = request.toJson();

            String response = httpPost(url, body.toString());
            if (response == null || response.isEmpty()) {
                Log.w(TAG, "Batch sync returned empty response");
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            int code = responseJson.optInt("code", -1);
            if (code != 0) {
                String msg = responseJson.optString("msg", "unknown error");
                Log.w(TAG, "Batch sync failed: code=" + code + " msg=" + msg);
                return null;
            }

            JSONObject dataObj = responseJson.optJSONObject("data");
            if (dataObj == null) {
                Log.w(TAG, "Batch sync response missing data");
                return null;
            }

            BehaviorBatchSyncResponseData result = BehaviorBatchSyncResponseData.fromJson(dataObj);
            Log.d(TAG, "Batch sync result: received=" + result.received
                    + " success=" + result.success + " failed=" + result.failed);
            return result;
        } catch (Exception e) {
            Log.w(TAG, "Batch sync failed", e);
            return null;
        }
    }

    private String httpPost(String urlStr, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (authToken != null && !authToken.isEmpty()) {
                conn.setRequestProperty("Authorization", authToken);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                Log.w(TAG, "HTTP error: " + responseCode);
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "HTTP POST failed: " + urlStr, e);
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return "";
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
