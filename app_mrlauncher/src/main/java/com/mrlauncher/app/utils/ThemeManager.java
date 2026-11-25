package com.mrlauncher.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.mrlauncher.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Handles the app color theme and the app background (preset color or custom image). */
public class ThemeManager {
    public static final String KEY_THEME = "appTheme";
    public static final String KEY_BACKGROUND = "appBackground";
    public static final String BG_DEFAULT = "default";
    public static final String BG_IMAGE = "image";
    private static final String IMAGE_NAME = "app_background.jpg";
    private static final int DIM_COLOR = 0x66000000;
    private static final int OPAQUE_BLACK = 0xFF000000;

    private ThemeManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    /** Applies the accent color overlay. Call before the activity inflates anything. */
    public static void applyTheme(Activity activity) {
        int overlay;
        switch (prefs(activity).getString(KEY_THEME, "blue")) {
            case "green": overlay = R.style.MrLauncher_Overlay_Green; break;
            case "red": overlay = R.style.MrLauncher_Overlay_Red; break;
            case "purple": overlay = R.style.MrLauncher_Overlay_Purple; break;
            case "orange": overlay = R.style.MrLauncher_Overlay_Orange; break;
            default: return;
        }
        activity.getTheme().applyStyle(overlay, true);
    }

    public static boolean hasCustomBackground(Context ctx) {
        return !BG_DEFAULT.equals(prefs(ctx).getString(KEY_BACKGROUND, BG_DEFAULT));
    }

    private static File imageFile(Context ctx) {
        return new File(ctx.getFilesDir(), IMAGE_NAME);
    }

    private static Integer presetColor(String value) {
        switch (value) {
            case "dark_blue": return 0xFF0B1530;
            case "dark_green": return 0xFF0D2416;
            case "dark_red": return 0xFF2A0F12;
            case "dark_purple": return 0xFF1E1233;
            case "gray": return 0xFF202124;
            default: return null;
        }
    }

    /** Sets the window background and makes the visible fragments see-through. */
    public static void applyBackground(Activity activity) {
        String value = prefs(activity).getString(KEY_BACKGROUND, BG_DEFAULT);
        Drawable bg = null;
        if (BG_IMAGE.equals(value)) {
            bg = loadImageBackground(activity);
        } else {
            Integer color = presetColor(value);
            if (color != null) bg = new ColorDrawable(color);
        }
        if (bg == null) {
            activity.getWindow().setBackgroundDrawable(new ColorDrawable(OPAQUE_BLACK));
        } else {
            activity.getWindow().setBackgroundDrawable(bg);
        }
        if (activity instanceof FragmentActivity) {
            refreshFragments(((FragmentActivity) activity).getSupportFragmentManager(), bg != null);
        }
    }

    private static void refreshFragments(FragmentManager fm, boolean custom) {
        for (Fragment f : fm.getFragments()) {
            View v = f.getView();
            if (v != null && custom) makeTransparent(v);
            refreshFragments(f.getChildFragmentManager(), custom);
        }
    }

    /** Replaces the opaque black root background of a fragment so the app background shows. */
    public static void makeTransparent(View root) {
        Drawable d = root.getBackground();
        if (d instanceof ColorDrawable && ((ColorDrawable) d).getColor() == OPAQUE_BLACK) {
            root.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private static Drawable loadImageBackground(Activity activity) {
        File f = imageFile(activity);
        if (!f.exists()) return null;
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (src == null) return null;
        Bitmap out = centerCrop(src, dm.widthPixels, dm.heightPixels);
        if (out != src) src.recycle();
        Drawable image = new BitmapDrawable(activity.getResources(), out);
        return new LayerDrawable(new Drawable[]{image, new ColorDrawable(DIM_COLOR)});
    }

    private static Bitmap centerCrop(Bitmap src, int w, int h) {
        if (w <= 0 || h <= 0) return src;
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        int cw = Math.min(src.getWidth(), Math.round(w / scale));
        int ch = Math.min(src.getHeight(), Math.round(h / scale));
        Rect from = new Rect((src.getWidth() - cw) / 2, (src.getHeight() - ch) / 2,
                (src.getWidth() - cw) / 2 + cw, (src.getHeight() - ch) / 2 + ch);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, from, new Rect(0, 0, w, h), null);
        return out;
    }

    /** Copies the picked image into app storage, downscaled. Returns true on success. */
    public static boolean importImage(Context ctx, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1920) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return false;
            try (OutputStream out = new FileOutputStream(imageFile(ctx))) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            bmp.recycle();
            prefs(ctx).edit().putString(KEY_BACKGROUND, BG_IMAGE).apply();
            return true;
        } catch (Exception | OutOfMemoryError e) {
            return false;
        }
    }

    public static void resetBackground(Context ctx) {
        File f = imageFile(ctx);
        if (f.exists()) f.delete();
        prefs(ctx).edit().putString(KEY_BACKGROUND, BG_DEFAULT).apply();
    }

    public static boolean hasImage(Context ctx) {
        return imageFile(ctx).exists();
    }
}
