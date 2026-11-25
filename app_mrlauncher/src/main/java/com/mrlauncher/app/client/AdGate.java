package com.mrlauncher.app.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.Toast;

import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.StartAppSDK;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;
import com.startapp.sdk.adsbase.adlisteners.VideoListener;

/**
 * Membuka fitur Client setelah menonton satu iklan rewarded dari Start.io.
 * Akses berlaku untuk 5 world, setelah itu kedaluwarsa dan iklan harus ditonton lagi.
 */
public final class AdGate {
    public static final String START_IO_APP_ID = "210980019";

    private static final String PREFS = "client_gate";
    private static final String KEY_UNLOCKED = "unlocked";
    private static final String KEY_WORLDS = "worlds_used";
    public static final int WORLD_LIMIT = 5;

    private AdGate() {}

    public interface Callback { void onUnlocked(); }

    public static boolean isUnlocked(Context context) {
        return prefs(context).getBoolean(KEY_UNLOCKED, false);
    }

    public static int worldsLeft(Context context) {
        return Math.max(0, WORLD_LIMIT - prefs(context).getInt(KEY_WORLDS, 0));
    }

    /** Menambah jumlah world yang dipakai. Mengembalikan true jika akses baru saja kedaluwarsa. */
    public static boolean addWorlds(Context context, int count) {
        if (!isUnlocked(context) || count <= 0) return false;
        int used = prefs(context).getInt(KEY_WORLDS, 0) + count;
        if (used > WORLD_LIMIT) {
            prefs(context).edit().putBoolean(KEY_UNLOCKED, false).putInt(KEY_WORLDS, 0).apply();
            return true;
        }
        prefs(context).edit().putInt(KEY_WORLDS, used).apply();
        return false;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void showUnlockDialog(Activity activity, Callback callback) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Please Watch Ads To unlock")
                .setPositiveButton("Watch Ads", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        Button watch = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        watch.setOnClickListener(v -> {
            watch.setEnabled(false);
            watch.setText("Loading...");
            loadAndShow(activity, dialog, watch, callback);
        });
    }

    private static void loadAndShow(Activity activity, AlertDialog dialog, Button watch, Callback callback) {
        try {
            StartAppSDK.init(activity, START_IO_APP_ID, false);
        } catch (Throwable t) {
            fail(activity, watch, "Ads failed to start");
            return;
        }

        final StartAppAd ad = new StartAppAd(activity);
        final boolean[] rewarded = {false};

        ad.setVideoListener(new VideoListener() {
            @Override
            public void onVideoCompleted() {
                rewarded[0] = true;
                prefs(activity).edit().putBoolean(KEY_UNLOCKED, true).putInt(KEY_WORLDS, 0).apply();
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "Unlocked", Toast.LENGTH_SHORT).show();
                    callback.onUnlocked();
                });
            }
        });

        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, new AdEventListener() {
            @Override
            public void onReceiveAd(Ad received) {
                activity.runOnUiThread(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    ad.showAd();
                });
            }

            @Override
            public void onFailedToReceiveAd(Ad failed) {
                activity.runOnUiThread(() -> fail(activity, watch, "Ad not available, try again"));
            }
        });
    }

    private static void fail(Activity activity, Button watch, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        watch.setEnabled(true);
        watch.setText("Watch Ads");
    }
}
