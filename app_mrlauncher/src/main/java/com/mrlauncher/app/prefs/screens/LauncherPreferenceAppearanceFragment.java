package com.mrlauncher.app.prefs.screens;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.mrlauncher.app.R;
import com.mrlauncher.app.utils.ThemeManager;

public class LauncherPreferenceAppearanceFragment extends LauncherPreferenceFragment {

    private final ActivityResultLauncher<String[]> mImagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onImagePicked);

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_appearance);

        ListPreference theme = requirePreference(ThemeManager.KEY_THEME, ListPreference.class);
        theme.setOnPreferenceChangeListener((p, value) -> {
            getPreferenceManager().getSharedPreferences().edit().putString(ThemeManager.KEY_THEME, (String) value).commit();
            if (getActivity() != null) getActivity().recreate();
            return true;
        });

        ListPreference background = requirePreference(ThemeManager.KEY_BACKGROUND, ListPreference.class);
        background.setOnPreferenceChangeListener((p, value) -> {
            if (ThemeManager.BG_IMAGE.equals(value) && !ThemeManager.hasImage(requireContext())) {
                mImagePicker.launch(new String[]{"image/*"});
                return false;
            }
            getPreferenceManager().getSharedPreferences().edit().putString(ThemeManager.KEY_BACKGROUND, (String) value).commit();
            ThemeManager.applyBackground(requireActivity());
            return true;
        });

        requirePreference("appBackgroundPick").setOnPreferenceClickListener(p -> {
            mImagePicker.launch(new String[]{"image/*"});
            return true;
        });

        requirePreference("appBackgroundReset").setOnPreferenceClickListener(p -> {
            ThemeManager.resetBackground(requireContext());
            background.setValue(ThemeManager.BG_DEFAULT);
            ThemeManager.applyBackground(requireActivity());
            return true;
        });
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        if (ThemeManager.importImage(requireContext(), uri)) {
            requirePreference(ThemeManager.KEY_BACKGROUND, ListPreference.class).setValue(ThemeManager.BG_IMAGE);
            ThemeManager.applyBackground(requireActivity());
        } else {
            Toast.makeText(requireContext(), R.string.preference_background_image_failed, Toast.LENGTH_LONG).show();
        }
    }
}
