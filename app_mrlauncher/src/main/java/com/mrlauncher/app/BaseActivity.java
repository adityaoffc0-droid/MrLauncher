package com.mrlauncher.app;

import android.content.*;
import android.content.res.Configuration;
import android.os.*;

import androidx.annotation.NonNull;
import android.view.View;
import androidx.appcompat.app.*;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.mrlauncher.app.utils.*;

import static com.mrlauncher.app.prefs.LauncherPreferences.PREF_IGNORE_NOTCH;

import com.mrlauncher.app.utils.LocaleUtils;
import com.mrlauncher.app.utils.ThemeManager;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleUtils.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        LocaleUtils.setLocale(this);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull View v, Bundle b) {
                if (usesAppBackground() && ThemeManager.hasCustomBackground(BaseActivity.this)) ThemeManager.makeTransparent(v);
            }
        }, true);
        Tools.setInsetsMode(this, setFullscreen(), shouldIgnoreNotch());
        Tools.getDisplayMetrics(this);
    }

    /** @return Whether the user-selected app background should be drawn behind this activity */
    protected boolean usesAppBackground() {
        return false;
    }

    /** @return Whether the activity should be set as a fullscreen one */
    public boolean setFullscreen(){
        return true;
    }


    @Override
    public void startActivity(Intent i) {
        super.startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (usesAppBackground()) ThemeManager.applyBackground(this);
        Tools.checkStorageInteractive(this);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        Tools.setInsetsMode(this, setFullscreen(), shouldIgnoreNotch());
        Tools.getDisplayMetrics(this);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        onMultiWindowModeChangedInner();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, @NonNull Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        onMultiWindowModeChangedInner();
    }

    private void onMultiWindowModeChangedInner() {
        boolean wantsFullscreen = setFullscreen();
        if(wantsFullscreen) {
            Tools.setInsetsMode(this, true, shouldIgnoreNotch());
        }
    }

    /** @return Whether or not the notch should be ignored */
    protected boolean shouldIgnoreNotch(){
        return PREF_IGNORE_NOTCH;
    }
}
