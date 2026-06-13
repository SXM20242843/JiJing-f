/*
 * NfcMarkerReader
 *
 * Reads NFC NDEF messages and extracts a marker_code from them.
 * Supports three formats:
 *   1. Plain marker_code: "NFC_LS_013"
 *   2. URL format:        "jj://nfc?marker_code=NFC_LS_013"
 *   3. JSON format:       {"marker_code":"NFC_LS_013"}
 *
 * Handles NDEF Text Record payload properly:
 *   - Parses status byte
 *   - Strips language code (e.g. "en", "zh")
 *   - Returns clean marker_code
 */

package com.live2d.demo.full;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NfcMarkerReader {

    private static final String TAG = "NfcMarkerReader";

    /**
     * Extract marker_code from an NDEF message.
     * Returns empty string if parsing fails.
     */
    public static String extractMarkerCode(NdefMessage ndefMessage) {
        if (ndefMessage == null) return "";

        NdefRecord[] records = ndefMessage.getRecords();
        if (records == null || records.length == 0) return "";

        for (NdefRecord record : records) {
            if (record == null) continue;

            String text = decodeNdefRecord(record);
            if (text == null || text.isEmpty()) continue;

            Log.d(TAG, "Decoded NDEF record text: [" + text + "]");

            String markerCode = parseMarkerCode(text);
            if (!markerCode.isEmpty()) {
                return markerCode;
            }
        }

        return "";
    }

    /**
     * Decode a single NDEF record to a string.
     * Handles TNF_WELL_KNOWN with RTD_TEXT properly (status byte + language code).
     */
    private static String decodeNdefRecord(NdefRecord record) {
        short tnf = record.getTnf();

        try {
            // TNF_WELL_KNOWN with RTD_TEXT
            if (tnf == NdefRecord.TNF_WELL_KNOWN) {
                byte[] type = record.getType();
                if (type != null && type.length > 0 && type[0] == 0x54) { // 'T' for Text
                    return decodeNdefTextRecord(record.getPayload());
                }
                // RTD_URI
                if (type != null && type.length > 0 && type[0] == 0x55) { // 'U' for URI
                    return decodeNdefUriRecord(record.getPayload());
                }
            }

            // TNF_ABSOLUTE_URI
            if (tnf == NdefRecord.TNF_ABSOLUTE_URI) {
                return record.toUri() != null ? record.toUri().toString() : "";
            }

            // Fallback: try to interpret payload as UTF-8
            byte[] payload = record.getPayload();
            if (payload != null && payload.length > 0) {
                return new String(payload, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode NDEF record", e);
        }

        return "";
    }

    /**
     * Decode NDEF Text Record payload.
     * Format: [status_byte][language_code...][text...]
     *   status_byte bit 7: 0=UTF8, 1=UTF16
     *   status_byte bits 5-0: language code length
     */
    private static String decodeNdefTextRecord(byte[] payload) {
        if (payload == null || payload.length < 2) return "";

        try {
            int statusByte = payload[0] & 0xFF;
            int languageCodeLength = statusByte & 0x3F; // bits 5-0
            int textStartIndex = 1 + languageCodeLength;

            if (textStartIndex >= payload.length) {
                // No text beyond language code — return empty
                return "";
            }

            int textLength = payload.length - textStartIndex;

            // Check encoding: bit 7
            boolean isUtf16 = (statusByte & 0x80) != 0;
            if (isUtf16) {
                // UTF-16 — each char is 2 bytes
                // Ensure even length
                if (textLength % 2 != 0) {
                    textLength -= 1; // trim trailing odd byte
                }
                return new String(payload, textStartIndex, textLength, StandardCharsets.UTF_16);
            } else {
                return new String(payload, textStartIndex, textLength, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode NDEF text record payload", e);
            // Fallback: return full payload as UTF-8
            try {
                return new String(payload, StandardCharsets.UTF_8).trim();
            } catch (Exception ex) {
                return "";
            }
        }
    }

    /**
     * Decode NDEF URI Record payload.
     * First byte is URI identifier code (prefix).
     */
    private static String decodeNdefUriRecord(byte[] payload) {
        if (payload == null || payload.length < 2) return "";

        try {
            int prefixCode = payload[0] & 0xFF;
            String prefix = getUriPrefix(prefixCode);
            String suffix = new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8);
            return prefix + suffix;
        } catch (Exception e) {
            return "";
        }
    }

    private static String getUriPrefix(int code) {
        // Only include common prefixes
        switch (code) {
            case 0x00: return "";
            case 0x01: return "http://www.";
            case 0x02: return "https://www.";
            case 0x03: return "http://";
            case 0x04: return "https://";
            default: return "";
        }
    }

    /**
     * Parse a text string into a marker_code.
     * Supports:
     *   1. Plain: "NFC_LS_013"
     *   2. URL:   "jj://nfc?marker_code=NFC_LS_013"
     *   3. JSON:  {"marker_code":"NFC_LS_013"}
     */
    static String parseMarkerCode(String rawText) {
        if (rawText == null) return "";

        String text = rawText.trim();
        if (text.isEmpty()) return "";

        // Remove newlines / carriage returns
        text = text.replace("\r", "").replace("\n", "").trim();

        // Try JSON first
        if (text.startsWith("{") && text.contains("marker_code")) {
            try {
                JSONObject json = new JSONObject(text);
                String code = json.optString("marker_code", json.optString("markerCode", ""));
                if (!code.isEmpty()) return code.trim();
            } catch (Exception ignored) {
                // Not valid JSON — fall through
            }
        }

        // Try URL format: jj://nfc?marker_code=XXX
        if (text.contains("marker_code=") || text.contains("markerCode=")) {
            String[] params = text.split("[?&]");
            for (String param : params) {
                if (param.startsWith("marker_code=")) {
                    return param.substring("marker_code=".length()).trim();
                }
                if (param.startsWith("markerCode=")) {
                    return param.substring("markerCode=".length()).trim();
                }
            }
        }

        // Try as plain marker_code
        // If it looks like a valid marker code (starts with NFC_ or similar), return directly
        String trimmed = text.trim();

        // Strip accidental language code prefixes ("enNFC_..." -> "NFC_...")
        trimmed = stripLanguageCodePrefix(trimmed);

        return trimmed;
    }

    /**
     * Strip accidental language code prefix.
     * NDEF Text Record raw payload may produce "enNFC_LS_013" instead of "NFC_LS_013".
     */
    private static String stripLanguageCodePrefix(String text) {
        if (text == null || text.isEmpty()) return "";

        // Common 2-char language codes that could leak into the text
        String[] langCodes = {"en", "zh", "ja", "ko", "fr", "de", "es", "it", "pt", "ru", "ar"};

        String lower = text.toLowerCase();
        for (String lang : langCodes) {
            if (lower.startsWith(lang) && text.length() > lang.length()) {
                char nextChar = text.charAt(lang.length());
                // Only strip if next char is uppercase (likely start of real marker)
                if (nextChar == 'N' || nextChar == 'A' || nextChar == 'S' || nextChar == 'M') {
                    String stripped = text.substring(lang.length());
                    Log.d(TAG, "Stripped language prefix '" + lang + "': [" + text + "] → [" + stripped + "]");
                    return stripped;
                }
            }
        }

        return text;
    }

    /**
     * Read raw NDEF from a Tag object (ReaderMode callback).
     * Handles RTD_TEXT with language code stripping.
     * Returns empty string if no NDEF data found.
     */
    public static String extractMarkerCodeFromTag(Tag tag) {
        if (tag == null) return "";

        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            Log.d(TAG, "tag does not support NDEF: " + tag);
            return "";
        }

        NdefMessage ndefMessage = null;
        try {
            // Try cached first (no IO)
            ndefMessage = ndef.getCachedNdefMessage();
        } catch (Exception ignored) {
            // Some devices throw on getCachedNdefMessage
        }

        if (ndefMessage == null) {
            try {
                ndef.connect();
                ndefMessage = ndef.getNdefMessage();
            } catch (Exception e) {
                Log.w(TAG, "Failed to read NDEF from tag", e);
                return "";
            } finally {
                try {
                    ndef.close();
                } catch (Exception ignored) {
                }
            }
        }

        if (ndefMessage == null) {
            Log.d(TAG, "No NDEF message on tag");
            return "";
        }

        return extractMarkerCode(ndefMessage);
    }
}
