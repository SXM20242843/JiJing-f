/*
 * NfcCheckinClient
 *
 * HTTP client for calling the backend NFC checkin endpoint.
 * POST /api/app/location/nfc-checkin
 *
 * Runs on background thread. Returns parsed response or null on failure.
 * Never throws exceptions that would crash the UI.
 */

package com.live2d.demo.full;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NfcCheckinClient {

    private static final String TAG = "NfcCheckinClient";

    private final String backendBaseUrl;
    private final String authToken;

    public NfcCheckinClient(String backendBaseUrl, String authToken) {
        this.backendBaseUrl = backendBaseUrl != null ? trimTrailingSlash(backendBaseUrl) : "";
        this.authToken = authToken != null ? authToken : "";
    }

    /**
     * Call POST /api/app/location/nfc-checkin.
     * Returns the response data or null on failure.
     *
     * Success condition: HTTP 2xx AND business code 0 or 200 AND data not empty.
     */
    public NfcCheckinResponseData checkin(NfcCheckinRequest request) {
        if (request == null) return null;

        try {
            String url = backendBaseUrl + "/api/app/location/nfc-checkin";
            JSONObject body = new JSONObject();
            body.put("user_id", request.userId);
            if (request.visitId != null && !request.visitId.isEmpty()) {
                body.put("visit_id", request.visitId);
            }
            if (request.areaId != null) {
                body.put("area_id", request.areaId);
            }
            body.put("marker_code", request.markerCode);
            body.put("client_time", request.clientTime);
            body.put("network_status", request.networkStatus);

            Log.d(TAG, "[NFC] checkin request url=" + url);
            Log.d(TAG, "[NFC] checkin request body=" + body.toString());

            // Capture HTTP status code from httpPost via out-parameter (Java 7 compatible)
            int[] httpStatusHolder = new int[1];
            httpStatusHolder[0] = -1;
            String response = httpPost(url, body.toString(), httpStatusHolder);
            int httpStatus = httpStatusHolder[0];

            Log.d(TAG, "[NFC] checkin responseBody=" + response);
            Log.d(TAG, "[NFC] checkin httpStatus=" + httpStatus);

            if (response == null || response.isEmpty()) {
                Log.w(TAG, "[NFC] checkin returned empty response, httpStatus=" + httpStatus);
                return null;
            }

            JSONObject responseJson = new JSONObject(response);
            int businessCode = responseJson.optInt("code", -1);
            String msg = responseJson.optString("msg", "");
            Log.d(TAG, "[NFC] checkin businessCode=" + businessCode + " msg=" + msg);

            // Success: HTTP 2xx AND (business code 0 or 200) AND data present
            boolean httpOk = httpStatus >= 200 && httpStatus < 300;
            boolean businessOk = businessCode == 0 || businessCode == 200;

            JSONObject dataObj = responseJson.optJSONObject("data");

            if (!httpOk || !businessOk || dataObj == null) {
                Log.w(TAG, "[NFC] checkin failed: httpStatus=" + httpStatus
                        + " businessCode=" + businessCode + " msg=" + msg
                        + " hasData=" + (dataObj != null));
                return null;
            }

            Log.d(TAG, "[NFC] checkin data raw=" + dataObj.toString());

            NfcCheckinResponseData result = NfcCheckinResponseData.fromJson(dataObj);
            if (result != null) {
                Log.d(TAG, "[NFC] checkin parsed success=true, spotId=" + result.spotId
                        + ", spotName=" + result.spotName
                        + ", sceneCode=" + result.sceneCode
                        + ", targetName=" + result.targetName
                        + ", markerCode=" + result.markerCode);
            } else {
                Log.w(TAG, "[NFC] checkin parse failed: fromJson returned null");
            }
            return result;
        } catch (Exception e) {
            Log.w(TAG, "[NFC] checkin failed with exception", e);
            return null;
        }
    }

    /**
     * HTTP POST with JSON body.
     * Writes the HTTP status code into outHttpStatus[0] on every call (success or error).
     * Returns the response body string, or null on HTTP error / exception.
     */
    private String httpPost(String urlStr, String jsonBody, int[] outHttpStatus) {
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

            // Write body
            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();

            // Always capture HTTP status for caller diagnostics
            if (outHttpStatus != null && outHttpStatus.length > 0) {
                outHttpStatus[0] = responseCode;
            }

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
            // Capture status even on exception (conn.getResponseCode() may still work)
            if (outHttpStatus != null && outHttpStatus.length > 0 && outHttpStatus[0] < 0 && conn != null) {
                try {
                    outHttpStatus[0] = conn.getResponseCode();
                } catch (Exception ignored) {}
            }
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
