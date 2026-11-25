package com.mrlauncher.app.client;

import android.os.SystemClock;
import android.view.Choreographer;

/**
 * Menghitung FPS game. Sumber utama: hitungan swap buffer dari native GLFW (akurat untuk SurfaceView dan TextureView).
 * Jika native belum terbaru: pada TextureView setiap frame yang dikirim game memanggil {@link #onGameFrame()}.
 * Pada SurfaceView tidak ada callback per frame, jadi dipakai hitungan Choreographer sebagai perkiraan.
 */
public final class FpsCounter {
    private static volatile int sGameFrames = 0;
    private static volatile boolean sGameFrameSeen = false;


    public static void onGameFrame() {
        sGameFrameSeen = true;
        sGameFrames++;
    }

    public interface Listener { void onFps(int fps); }

    private final Listener mListener;
    private final Choreographer mChoreographer = Choreographer.getInstance();
    private long mLastTime;
    private int mChoreoFrames;
    private int mLastGameFrames;
    private boolean mRunning;
    private boolean mNativeOk = true;
    private int mLastNativeFrames = -1;

    private final Choreographer.FrameCallback mCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mRunning) return;
            mChoreoFrames++;
            long now = SystemClock.elapsedRealtime();
            long elapsed = now - mLastTime;
            if (elapsed >= 1000) {
                int fps;
                int nativeFrames = readNativeFrames();
                int nativeDelta = 0;
                if (nativeFrames >= 0) {
                    if (mLastNativeFrames < 0) mLastNativeFrames = nativeFrames;
                    nativeDelta = nativeFrames - mLastNativeFrames;
                    mLastNativeFrames = nativeFrames;
                }
                if (nativeDelta > 0) {
                    fps = Math.round(nativeDelta * 1000f / elapsed);
                } else if (sGameFrameSeen) {
                    int frames = sGameFrames;
                    fps = Math.round((frames - mLastGameFrames) * 1000f / elapsed);
                    mLastGameFrames = frames;
                } else {
                    fps = Math.round(mChoreoFrames * 1000f / elapsed);
                }
                mChoreoFrames = 0;
                mLastTime = now;
                mListener.onFps(fps);
            }
            mChoreographer.postFrameCallback(this);
        }
    };

    /** Jumlah frame yang sudah di-swap game (hanya backend GLFW), atau -1 jika tidak tersedia. */
    private int readNativeFrames() {
        if (!mNativeOk) return -1;
        try {
            return git.artdeell.dnbootstrap.glfw.GLFW.nativeGetFrameCount();
        } catch (Throwable t) {
            mNativeOk = false;
            return -1;
        }
    }

    public FpsCounter(Listener listener) {
        mListener = listener;
    }

    public void start() {
        if (mRunning) return;
        mRunning = true;
        mLastTime = SystemClock.elapsedRealtime();
        mChoreoFrames = 0;
        mLastGameFrames = sGameFrames;
        mChoreographer.postFrameCallback(mCallback);
    }

    public void stop() {
        mRunning = false;
        mChoreographer.removeFrameCallback(mCallback);
    }
}
