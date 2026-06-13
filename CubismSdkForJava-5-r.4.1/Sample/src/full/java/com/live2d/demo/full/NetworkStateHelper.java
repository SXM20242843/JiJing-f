/*
 * NetworkStateHelper
 *
 * Simple network state detection using ConnectivityManager.
 * Returns NORMAL, WEAK, or OFFLINE.
 *
 * Current implementation:
 *   - OFFLINE: no active network
 *   - WEAK:   connected but on mobile data (cellular) — treated as potentially weak
 *   - NORMAL: connected via WiFi or Ethernet
 *
 * Does NOT introduce complex third-party libraries.
 */

package com.live2d.demo.full;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

public class NetworkStateHelper {

    private static final String TAG = "NetworkStateHelper";

    private final Context context;
    private final ConnectivityManager connectivityManager;

    public NetworkStateHelper(Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager)
                this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Get current network level.
     */
    public NetworkLevel getNetworkLevel() {
        if (!isNetworkAvailable()) {
            return NetworkLevel.OFFLINE;
        }
        if (isWeakNetwork()) {
            return NetworkLevel.WEAK;
        }
        return NetworkLevel.NORMAL;
    }

    /**
     * Check if any network is available.
     */
    public boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) return false;
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check network availability", e);
            return false;
        }
    }

    /**
     * Check if current network is potentially weak.
     * Treats cellular/mobile data as WEAK; WiFi/Ethernet as NORMAL.
     */
    public boolean isWeakNetwork() {
        if (connectivityManager == null) return false;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) return false;
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (caps == null) return false;

                // WiFi is strong
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return false;
                }

                // Cellular is weak
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return true;
                }

                return false;
            } else {
                NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
                if (activeNetwork == null) return false;

                int type = activeNetwork.getType();
                if (type == ConnectivityManager.TYPE_WIFI || type == ConnectivityManager.TYPE_ETHERNET) {
                    return false;
                }
                if (type == ConnectivityManager.TYPE_MOBILE) {
                    return true;
                }
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check weak network", e);
            return false;
        }
    }

    /**
     * Get a human-readable network status string.
     */
    public String getNetworkStatusString() {
        NetworkLevel level = getNetworkLevel();
        switch (level) {
            case NORMAL: return "NORMAL";
            case WEAK:   return "WEAK";
            case OFFLINE: return "OFFLINE";
            default:     return "NORMAL";
        }
    }
}
