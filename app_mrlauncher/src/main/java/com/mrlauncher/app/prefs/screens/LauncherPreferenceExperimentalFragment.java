package com.mrlauncher.app.prefs.screens;

import android.os.Bundle;

import androidx.preference.SwitchPreferenceCompat;

import com.mrlauncher.app.utils.GpuUtils;

import com.mrlauncher.app.R;

public class LauncherPreferenceExperimentalFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_experimental);
        SwitchPreferenceCompat sysmem = requirePreference("freedrenoSysmem", SwitchPreferenceCompat.class);
        SwitchPreferenceCompat ubwc = requirePreference("ubwcWorkaround", SwitchPreferenceCompat.class);
        boolean hasFreedreno = GpuUtils.getGlInfo().isAdreno();
        sysmem.setVisible(hasFreedreno);
        ubwc.setVisible(hasFreedreno);
    }
}
