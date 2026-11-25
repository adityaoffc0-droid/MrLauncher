package com.mrlauncher.app.client;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import java.util.List;

/**
 * Tombol "Client", panel fitur (Client PvP dan Client Survival) dan tampilan FPS saat bermain.
 * Semua view ditambahkan lewat kode ke layout kontrol game.
 */
public class ClientUI {
    private final Activity mActivity;
    private final FrameLayout mRoot;
    private final ClientEngine mEngine = new ClientEngine();
    private final FpsCounter mFps;

    private final TextView mFpsText;
    private final View mTopBar;
    private final LinearLayout mPanel;
    private final LinearLayout mList;
    private final TextView mTabPvp;
    private final TextView mTabSurvival;
    private boolean mShowSurvival;
    private TextView mTitle;
    private boolean mEditMode;
    private boolean mDragging;
    private float mDownRawX, mDownRawY, mStartX, mStartY;
    private float mFracX = -1, mFracY = -1;
    private final SharedPreferences mPosPrefs;
    private final WorldCounter mWorldCounter = new WorldCounter();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mWorldPoll = new Runnable() {
        @Override
        public void run() {
            int n = mWorldCounter.poll();
            if (n > 0 && AdGate.addWorlds(mActivity, n)) {
                mEngine.stopAll();
                mPanel.setVisibility(View.GONE);
                Toast.makeText(mActivity, "Client expired. Watch ads to unlock again", Toast.LENGTH_LONG).show();
            }
            updateTitle();
            mHandler.postDelayed(this, 1500);
        }
    };

    public ClientUI(Activity activity, FrameLayout root) {
        mActivity = activity;
        mRoot = root;
        mPosPrefs = activity.getSharedPreferences("client_pos", Activity.MODE_PRIVATE);
        mFracX = mPosPrefs.getFloat("fx", -1);
        mFracY = mPosPrefs.getFloat("fy", -1);

        // Bar kiri atas: tombol Client + FPS
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setElevation(dp(12));

        TextView clientBtn = makeText("Client", 13, Color.WHITE, true);
        clientBtn.setBackground(round(0xCC1E88E5, 8));
        clientBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        clientBtn.setOnClickListener(v -> { if (!mEditMode) togglePanel(); });
        bar.addView(clientBtn);

        mFpsText = makeText("Fps : 0", 13, Color.WHITE, true);
        mFpsText.setBackground(round(0x99000000, 8));
        mFpsText.setPadding(dp(10), dp(6), dp(10), dp(6));
        mFpsText.setShadowLayer(2f, 1f, 1f, Color.BLACK);
        LinearLayout.LayoutParams fpsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fpsLp.leftMargin = dp(8);
        bar.addView(mFpsText, fpsLp);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        barLp.leftMargin = dp(90);
        barLp.topMargin = dp(36);
        mRoot.addView(bar, barLp);
        mTopBar = bar;
        bar.setOnTouchListener(this::onBarTouch);
        mRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (!mDragging) applySavedPosition();
        });

        // Panel
        mPanel = new LinearLayout(activity);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        mPanel.setBackground(round(0xEE15181F, 12));
        mPanel.setPadding(dp(10), dp(10), dp(10), dp(10));
        mPanel.setClickable(true);
        mPanel.setElevation(dp(16));
        mPanel.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = makeText("Client", 16, Color.WHITE, true);
        mTitle = title;
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView stop = makeText("Turn all off", 12, 0xFFFF8A80, true);
        stop.setPadding(dp(8), dp(4), dp(8), dp(4));
        stop.setOnClickListener(v -> {
            mEngine.stopAll();
            refreshList();
        });
        header.addView(stop);
        TextView close = makeText("✕", 16, Color.WHITE, true);
        close.setPadding(dp(12), dp(4), dp(4), dp(4));
        close.setOnClickListener(v -> mPanel.setVisibility(View.GONE));
        header.addView(close);
        mPanel.addView(header);

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        mTabPvp = makeText("Client PvP", 13, Color.WHITE, true);
        mTabSurvival = makeText("Client Survival", 13, Color.WHITE, true);
        for (TextView t : new TextView[]{mTabPvp, mTabSurvival}) {
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(8), dp(6), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.topMargin = dp(8);
            lp.rightMargin = dp(4);
            tabs.addView(t, lp);
        }
        mTabPvp.setOnClickListener(v -> { mShowSurvival = false; refreshList(); });
        mTabSurvival.setOnClickListener(v -> { mShowSurvival = true; refreshList(); });
        mPanel.addView(tabs);

        ScrollView scroll = new ScrollView(activity);
        mList = new LinearLayout(activity);
        mList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mList);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(6);
        mPanel.addView(scroll, scrollLp);

        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int panelW = Math.min(dp(320), (int) (screenW * 0.7f));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelW,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.TOP);
        panelLp.leftMargin = dp(8);
        panelLp.topMargin = dp(52);
        panelLp.bottomMargin = dp(8);
        mRoot.addView(mPanel, panelLp);

        mFps = new FpsCounter(fps -> mFpsText.setText("Fps : " + fps));
        refreshList();
    }

    private void updateTitle() {
        if (mTitle == null) return;
        mTitle.setText(AdGate.isUnlocked(mActivity)
                ? "Client (" + AdGate.worldsLeft(mActivity) + " worlds left)" : "Client");
    }

    public void onResume() {
        mFps.start();
        mHandler.removeCallbacks(mWorldPoll);
        mHandler.post(mWorldPoll);
    }

    /** Dipanggil saat game tidak di layar: semua fitur dimatikan agar tombol tidak tertahan. */
    public void onPause() {
        mFps.stop();
        mHandler.removeCallbacks(mWorldPoll);
        mEngine.stopAll();
        refreshList();
    }

    public void onDestroy() {
        mFps.stop();
        mHandler.removeCallbacks(mWorldPoll);
        mEngine.stopAll();
    }

    /** Mode editor kontrol: tombol Client bisa digeser dan posisinya disimpan. */
    public void setEditMode(boolean edit) {
        mEditMode = edit;
        mTopBar.setVisibility(View.VISIBLE);
        mTopBar.setBackground(edit ? outline() : null);
        if (edit) {
            mPanel.setVisibility(View.GONE);
            Toast.makeText(mActivity, "Drag the Client button to move it", Toast.LENGTH_LONG).show();
        }
    }

    private GradientDrawable outline() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x22FFFFFF);
        d.setStroke(dp(2), 0xFFFFC107);
        d.setCornerRadius(dp(10));
        return d;
    }

    private boolean onBarTouch(View v, MotionEvent e) {
        if (!mEditMode) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = true;
                mDownRawX = e.getRawX();
                mDownRawY = e.getRawY();
                mStartX = v.getX();
                mStartY = v.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                v.setX(clamp(mStartX + e.getRawX() - mDownRawX, 0, Math.max(0, mRoot.getWidth() - v.getWidth())));
                v.setY(clamp(mStartY + e.getRawY() - mDownRawY, 0, Math.max(0, mRoot.getHeight() - v.getHeight())));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                if (mRoot.getWidth() > 0 && mRoot.getHeight() > 0) {
                    mFracX = v.getX() / mRoot.getWidth();
                    mFracY = v.getY() / mRoot.getHeight();
                    mPosPrefs.edit().putFloat("fx", mFracX).putFloat("fy", mFracY).apply();
                }
                return true;
        }
        return false;
    }

    private void applySavedPosition() {
        if (mFracX < 0 || mFracY < 0 || mRoot.getWidth() == 0 || mTopBar.getWidth() == 0) return;
        mTopBar.setX(clamp(mFracX * mRoot.getWidth(), 0, Math.max(0, mRoot.getWidth() - mTopBar.getWidth())));
        mTopBar.setY(clamp(mFracY * mRoot.getHeight(), 0, Math.max(0, mRoot.getHeight() - mTopBar.getHeight())));
    }

    /** Menaruh panel tepat di bawah tombol Client, atau di atasnya jika ruang bawah sempit. */
    private void placePanel() {
        int rootW = mRoot.getWidth(), rootH = mRoot.getHeight();
        if (rootW == 0 || rootH == 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
        int panelW = lp.width;
        int x = (int) clamp(mTopBar.getX(), dp(4), Math.max(dp(4), rootW - panelW - dp(4)));
        int below = (int) (mTopBar.getY() + mTopBar.getHeight() + dp(4));
        int minH = dp(170);
        int y, h;
        if (rootH - below - dp(8) >= minH) {
            y = below;
            h = rootH - below - dp(8);
        } else {
            h = (int) Math.min(rootH - dp(16), Math.max(minH, mTopBar.getY() - dp(8)));
            y = (int) Math.max(dp(8), mTopBar.getY() - dp(4) - h);
        }
        lp.gravity = Gravity.START | Gravity.TOP;
        lp.leftMargin = x;
        lp.topMargin = y;
        lp.bottomMargin = 0;
        lp.height = h;
        mPanel.setLayoutParams(lp);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void togglePanel() {
        if (mPanel.getVisibility() != View.VISIBLE && !AdGate.isUnlocked(mActivity)) {
            AdGate.showUnlockDialog(mActivity, this::togglePanel);
            return;
        }
        boolean open = mPanel.getVisibility() != View.VISIBLE;
        if (open) placePanel();
        mPanel.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) { refreshList(); updateTitle(); }
    }

    private void refreshList() {
        styleTab(mTabPvp, !mShowSurvival);
        styleTab(mTabSurvival, mShowSurvival);
        mList.removeAllViews();
        List<ClientEngine.Feature> features = mShowSurvival ? mEngine.getSurvival() : mEngine.getPvp();

        if (!mShowSurvival) {
            LinearLayout cpsRow = row();
            TextView cpsLabel = makeText("CPS Auto Clicker : " + mEngine.getCps(), 13, 0xFFB0BEC5, false);
            cpsRow.addView(cpsLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView change = makeText("Change", 12, Color.WHITE, true);
            change.setBackground(round(0xFF37474F, 6));
            change.setPadding(dp(12), dp(6), dp(12), dp(6));
            change.setOnClickListener(v -> {
                int cps = mEngine.cycleCps();
                cpsLabel.setText("CPS Auto Clicker : " + cps);
            });
            cpsRow.addView(change);
            mList.addView(cpsRow);
        }

        for (ClientEngine.Feature f : features) mList.addView(buildRow(f));
    }

    private View buildRow(ClientEngine.Feature f) {
        LinearLayout row = row();
        LinearLayout texts = new LinearLayout(mActivity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(makeText(f.name, 14, Color.WHITE, true));
        texts.addView(makeText(f.desc, 11, 0xFF90A4AE, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (f.action) {
            TextView run = makeText("Run", 12, Color.WHITE, true);
            run.setBackground(round(0xFF1E88E5, 6));
            run.setPadding(dp(12), dp(6), dp(12), dp(6));
            run.setOnClickListener(v -> mEngine.press(f));
            row.addView(run);
        } else {
            SwitchCompat sw = new SwitchCompat(mActivity);
            sw.setChecked(f.isEnabled());
            sw.setOnClickListener(v -> sw.setChecked(mEngine.press(f)));
            row.addView(sw);
        }
        return row;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        row.setBackground(round(0x33FFFFFF, 8));
        return row;
    }

    private void styleTab(TextView tab, boolean active) {
        tab.setBackground(round(active ? 0xFF1E88E5 : 0xFF2A2F3A, 8));
    }

    private TextView makeText(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int v) {
        return Math.round(v * mActivity.getResources().getDisplayMetrics().density);
    }
}
