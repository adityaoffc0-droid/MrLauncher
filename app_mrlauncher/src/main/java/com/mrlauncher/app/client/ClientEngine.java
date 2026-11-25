package com.mrlauncher.app.client;

import static com.mrlauncher.app.game.platform.Platform.PLATFORM;

import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import com.mrlauncher.app.CallbackBridge;
import com.mrlauncher.app.game.platform.Platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mesin fitur Client. Semua fitur bekerja dengan mengirim input (tombol keyboard dan klik mouse)
 * ke game, jadi memakai keybind bawaan Minecraft. Input hanya dikirim saat game menangkap kursor
 * (sedang bermain), supaya tidak salah klik di inventory atau menu.
 */
public final class ClientEngine {
    public static final int MOUSE_LEFT = 0;
    public static final int MOUSE_RIGHT = 1;

    public static final class Feature {
        public final String id;
        public final String name;
        public final String desc;
        /** true: tombol aksi sekali tekan, false: saklar on/off */
        public final boolean action;
        boolean enabled;
        final Runnable onRun;

        Feature(String id, String name, String desc, boolean action, Runnable onRun) {
            this.id = id; this.name = name; this.desc = desc; this.action = action; this.onRun = onRun;
        }

        public boolean isEnabled() { return enabled; }
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SparseIntArray mKeyRefs = new SparseIntArray();
    private final SparseIntArray mKeyDown = new SparseIntArray();
    private final SparseIntArray mMouseRefs = new SparseIntArray();
    private final SparseIntArray mMouseDown = new SparseIntArray();
    private final Map<String, Feature> mFeatures = new LinkedHashMap<>();
    private final List<Feature> mPvp = new ArrayList<>();
    private final List<Feature> mSurvival = new ArrayList<>();

    private int mCps = 10;
    private int mTick = 0;
    private boolean mTicking;
    private boolean mLastGrab;

    private static final int TICK_MS = 25;

    private final Runnable mTickRunnable = new Runnable() {
        @Override
        public void run() {
            tick();
            if (mTicking) mHandler.postDelayed(this, TICK_MS);
        }
    };

    public ClientEngine() {
        buildFeatures();
    }

    public List<Feature> getPvp() { return mPvp; }
    public List<Feature> getSurvival() { return mSurvival; }

    public int getCps() { return mCps; }

    public int cycleCps() {
        mCps = mCps >= 20 ? 6 : mCps + 2;
        return mCps;
    }

    private void buildFeatures() {
        // Client PvP (15)
        addPvp(toggle("sprint", "Toggle Sprint", "Keep sprinting without holding (Ctrl)"));
        addPvp(toggle("sneak", "Toggle Sneak", "Keep sneaking (Shift)"));
        addPvp(toggle("autoclick", "Auto Clicker", "Automatic left click at set CPS"));
        addPvp(toggle("holdattack", "Hold Attack", "Hold left click"));
        addPvp(toggle("holdblock", "Hold Block", "Hold right click (sword block / eat)"));
        addPvp(toggle("autojump", "Auto Jump", "Keep jumping (Space)"));
        addPvp(toggle("autowalk", "Auto Walk", "Keep walking forward (W)"));
        addPvp(toggle("blockhit", "Block Hit", "Right click + left click combo"));
        addPvp(toggle("strafe", "Auto Strafe", "Alternate left and right (A/D)"));
        addPvp(toggle("crit", "Crit Helper", "Jump, then attack while falling"));
        addPvp(action("offhand", "Swap Offhand", "Swap item to offhand (F)", () -> tapKey(KeyEvent.KEYCODE_F)));
        addPvp(action("drop", "Drop Item", "Drop held item (Q)", () -> tapKey(KeyEvent.KEYCODE_Q)));
        addPvp(toggle("tablist", "Tab List", "Keep player list shown (Tab)"));
        addPvp(action("camera", "Change Camera", "Change camera view (F5)", () -> tapKey(KeyEvent.KEYCODE_F5)));
        addPvp(action("hud", "Hide HUD", "Show or hide HUD (F1)", () -> tapKey(KeyEvent.KEYCODE_F1)));

        // Client Survival (5)
        addSurvival(toggle("automine", "Auto Mine", "Hold left click to keep mining"));
        addSurvival(toggle("autouse", "Auto Use", "Automatic right click (place blocks, plant, etc.)"));
        addSurvival(toggle("autorun", "Auto Run", "Keep walking, sprinting, and jumping"));
        addSurvival(action("screenshot", "Screenshot", "Take a game screenshot (F2)", () -> tapKey(KeyEvent.KEYCODE_F2)));
        addSurvival(action("debug", "Debug Info", "Show or hide debug info (F3)", () -> tapKey(KeyEvent.KEYCODE_F3)));
    }

    private Feature toggle(String id, String name, String desc) {
        Feature f = new Feature(id, name, desc, false, null);
        mFeatures.put(id, f);
        return f;
    }

    private Feature action(String id, String name, String desc, Runnable run) {
        Feature f = new Feature(id, name, desc, true, run);
        mFeatures.put(id, f);
        return f;
    }

    private void addPvp(Feature f) { mPvp.add(f); }
    private void addSurvival(Feature f) { mSurvival.add(f); }

    /** Menjalankan aksi, atau membalik status saklar. Mengembalikan status terbaru. */
    public boolean press(Feature f) {
        if (f.action) {
            if (Platform.isGrabbing() || f.id.equals("screenshot") || f.id.equals("debug")) f.onRun.run();
            return false;
        }
        f.enabled = !f.enabled;
        mTick = 0;
        startTicking();
        return f.enabled;
    }

    private boolean on(String id) {
        Feature f = mFeatures.get(id);
        return f != null && f.enabled;
    }

    private void startTicking() {
        if (mTicking) return;
        mTicking = true;
        mHandler.post(mTickRunnable);
    }

    /** Turn all off saklar dan lepas semua tombol. */
    public void stopAll() {
        for (Feature f : mFeatures.values()) f.enabled = false;
        mTicking = false;
        mHandler.removeCallbacks(mTickRunnable);
        releaseEverything();
    }

    private boolean anyEnabled() {
        for (Feature f : mFeatures.values()) if (!f.action && f.enabled) return true;
        return false;
    }

    private void tick() {
        mTick++;
        boolean grab = Platform.isGrabbing();
        if (!grab) {
            if (mLastGrab) releaseEverything();
            mLastGrab = false;
            if (!anyEnabled()) mTicking = false;
            return;
        }
        mLastGrab = true;

        int ms = mTick * TICK_MS;

        // Saklar tahan (key / mouse ditahan selama aktif)
        setKeyHeld(KeyEvent.KEYCODE_CTRL_LEFT, "sprint", on("sprint") || on("autorun"));
        setKeyHeld(KeyEvent.KEYCODE_SHIFT_LEFT, "sneak", on("sneak"));
        setKeyHeld(KeyEvent.KEYCODE_TAB, "tablist", on("tablist"));
        setKeyHeld(KeyEvent.KEYCODE_W, "walk", on("autowalk") || on("autorun"));
        setKeyHeld(KeyEvent.KEYCODE_SPACE, "jump", on("autojump") || on("autorun"));
        setMouseHeld(MOUSE_LEFT, "attack", on("holdattack") || on("automine"));
        setMouseHeld(MOUSE_RIGHT, "block", on("holdblock"));

        // Auto Clicker: satu klik tiap 1000/CPS ms
        if (on("autoclick")) {
            int period = Math.max(1, Math.round(1000f / mCps / TICK_MS));
            if (mTick % period == 0) clickMouse(MOUSE_LEFT);
        }

        // Auto Use: klik kanan 5x per detik
        if (on("autouse") && ms % 200 == 0) clickMouse(MOUSE_RIGHT);

        // Block Hit: siklus 500 ms (kanan turun, klik kiri, kanan naik)
        if (on("blockhit")) {
            int phase = ms % 500;
            if (phase == 0) pulseMouseDown(MOUSE_RIGHT, true);
            else if (phase == 75) clickMouse(MOUSE_LEFT);
            else if (phase == 150) pulseMouseDown(MOUSE_RIGHT, false);
        } else {
            pulseMouseDown(MOUSE_RIGHT, false);
        }

        // Auto Strafe: ganti arah tiap 400 ms
        if (on("strafe")) {
            boolean left = (ms / 400) % 2 == 0;
            setKeyHeld(KeyEvent.KEYCODE_A, "strafeA", left);
            setKeyHeld(KeyEvent.KEYCODE_D, "strafeD", !left);
        } else {
            setKeyHeld(KeyEvent.KEYCODE_A, "strafeA", false);
            setKeyHeld(KeyEvent.KEYCODE_D, "strafeD", false);
        }

        // Crit Helper: lompat, serang saat sudah di puncak (sekitar 325 ms), siklus 750 ms
        if (on("crit")) {
            int phase = ms % 750;
            if (phase == 0) pulseKey(KeyEvent.KEYCODE_SPACE, "critjump", true);
            else if (phase == 100) pulseKey(KeyEvent.KEYCODE_SPACE, "critjump", false);
            else if (phase == 325) clickMouse(MOUSE_LEFT);
        } else {
            pulseKey(KeyEvent.KEYCODE_SPACE, "critjump", false);
        }

        if (!anyEnabled()) {
            releaseEverything();
            mTicking = false;
        }
    }

    // ---- Penahan tombol dengan hitung referensi per sumber ----

    private final Map<String, Boolean> mSources = new LinkedHashMap<>();

    private void setKeyHeld(int key, String source, boolean held) {
        String k = "k" + key + source;
        boolean was = Boolean.TRUE.equals(mSources.get(k));
        if (held == was) return;
        mSources.put(k, held);
        int refs = mKeyRefs.get(key) + (held ? 1 : -1);
        mKeyRefs.put(key, Math.max(0, refs));
        applyKey(key);
    }

    private void pulseKey(int key, String source, boolean down) {
        setKeyHeld(key, source, down);
    }

    private void applyKey(int key) {
        boolean shouldDown = mKeyRefs.get(key) > 0;
        boolean isDown = mKeyDown.get(key) > 0;
        if (shouldDown == isDown) return;
        mKeyDown.put(key, shouldDown ? 1 : 0);
        if (key == KeyEvent.KEYCODE_CTRL_LEFT || key == KeyEvent.KEYCODE_SHIFT_LEFT)
            CallbackBridge.setModifiers(key, shouldDown);
        PLATFORM.sendKeyEvent(key, shouldDown, CallbackBridge.getCurrentMods());
    }

    private void setMouseHeld(int button, String source, boolean held) {
        String k = "m" + button + source;
        boolean was = Boolean.TRUE.equals(mSources.get(k));
        if (held == was) return;
        mSources.put(k, held);
        int refs = mMouseRefs.get(button) + (held ? 1 : -1);
        mMouseRefs.put(button, Math.max(0, refs));
        applyMouse(button);
    }

    private void pulseMouseDown(int button, boolean down) {
        setMouseHeld(button, "pulse", down);
    }

    private void applyMouse(int button) {
        boolean shouldDown = mMouseRefs.get(button) > 0;
        boolean isDown = mMouseDown.get(button) > 0;
        if (shouldDown == isDown) return;
        mMouseDown.put(button, shouldDown ? 1 : 0);
        Platform.sendMouseButtonEvent(button, shouldDown ? 1 : 0, CallbackBridge.getCurrentMods());
    }

    /** Klik cepat. Jika tombol sedang ditahan oleh fitur lain, dilepas sebentar lalu ditekan lagi. */
    private void clickMouse(int button) {
        final boolean wasDown = mMouseDown.get(button) > 0;
        int mods = CallbackBridge.getCurrentMods();
        if (wasDown) Platform.sendMouseButtonEvent(button, 0, mods);
        Platform.sendMouseButtonEvent(button, 1, mods);
        mHandler.postDelayed(() -> {
            Platform.sendMouseButtonEvent(button, 0, CallbackBridge.getCurrentMods());
            if (wasDown && mMouseRefs.get(button) > 0)
                Platform.sendMouseButtonEvent(button, 1, CallbackBridge.getCurrentMods());
        }, 20);
    }

    private void tapKey(int key) {
        PLATFORM.sendKeyEvent(key, true, CallbackBridge.getCurrentMods());
        PLATFORM.sendKeyEvent(key, false, CallbackBridge.getCurrentMods());
    }

    private void releaseEverything() {
        for (int i = 0; i < mKeyDown.size(); i++) {
            int key = mKeyDown.keyAt(i);
            if (mKeyDown.valueAt(i) > 0) {
                mKeyDown.put(key, 0);
                if (key == KeyEvent.KEYCODE_CTRL_LEFT || key == KeyEvent.KEYCODE_SHIFT_LEFT)
                    CallbackBridge.setModifiers(key, false);
                PLATFORM.sendKeyEvent(key, false, CallbackBridge.getCurrentMods());
            }
        }
        for (int i = 0; i < mMouseDown.size(); i++) {
            int b = mMouseDown.keyAt(i);
            if (mMouseDown.valueAt(i) > 0) {
                mMouseDown.put(b, 0);
                Platform.sendMouseButtonEvent(b, 0, CallbackBridge.getCurrentMods());
            }
        }
        mKeyRefs.clear();
        mMouseRefs.clear();
        mSources.clear();
    }
}
