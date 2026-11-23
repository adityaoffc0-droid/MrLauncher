package com.mrlauncher.app.prefs.screens;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.mrlauncher.app.BuildConfig;
import com.mrlauncher.app.R;

import com.mrlauncher.app.contracts.OpenDocumentWithExtension;
import com.mrlauncher.app.game.renderer.RendererCache;
import com.mrlauncher.app.game.renderer.ext.ExternalRenderer;
import com.mrlauncher.app.game.renderer.ext.ExternalRendererManager;
import com.mrlauncher.app.MrLauncherApplication;
import android.net.Uri;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.util.List;
import com.mrlauncher.app.game.renderer.extra.GLESProvider;
import com.mrlauncher.app.prefs.CustomSeekBarPreference;
import com.mrlauncher.app.prefs.LauncherPreferences;
import com.mrlauncher.app.utils.GpuUtils;
import com.mrlauncher.app.vulkan.DriverInstaller;
import com.mrlauncher.app.vulkan.DriverManager;
import com.mrlauncher.app.vulkan.driver.BuiltinDriver;
import com.mrlauncher.app.vulkan.driver.Driver;
import com.mrlauncher.app.vulkan.ui.DriverConfigDialog;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {
    private Boolean hasAngle = null;

    private DriverConfigDialog mDialogScreen;
    private final ActivityResultLauncher<Object> mInstallDriver =
            registerForActivityResult(new OpenDocumentWithExtension("zip"), data -> {
                if(data != null) DriverInstaller.installDriverFromUri(getContext(), data, this::openDriverDialog);
            });
    private final ActivityResultLauncher<Object> mImportRenderer =
            registerForActivityResult(new OpenDocumentWithExtension("so"), data -> {
                if(data != null) importRenderer(data);
            });

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        int resolution = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);

        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");

        // #724 bug fix
        if (resolution < 25) {
            resolutionSeekbar.setValue(100);
        } else {
            resolutionSeekbar.setValue(resolution);
        }

        // Sustained performance is only available since Nougat
        SwitchPreferenceCompat sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreferenceCompat.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustainedPerfSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);

        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);

        // Show ANGLE switch only if AnglePlugin is available
        if(hasAngle == null) {
            GLESProvider provider = GLESProvider.getGlesProvider(getContext(), true);
            hasAngle = provider instanceof GLESProvider.ExternalAngleProvider || provider instanceof GLESProvider.SystemAngleProvider;
        }

        SwitchPreferenceCompat angleSwitch = requirePreference("use_angle", SwitchPreferenceCompat.class);
        angleSwitch.setVisible(hasAngle);
        angleSwitch.setChecked(LauncherPreferences.PREF_USE_ANGLE);

        ListPreference rendererListPreference = requirePreference("renderer",
                ListPreference.class);
        RendererCache list = RendererCache.getCompatibleRenderers(getContext());
        rendererListPreference.setEntries(list.rendererDisplayNames);
        rendererListPreference.setEntryValues(list.rendererIds.toArray(new String[0]));

        requirePreference("importRenderer").setOnPreferenceClickListener(pref -> {
            mImportRenderer.launch(null);
            return true;
        });
        requirePreference("manageRenderers").setOnPreferenceClickListener(pref -> {
            showRendererManager();
            return true;
        });


        Preference drivers = requirePreference("manageDrivers");
        drivers.setOnPreferenceClickListener(pref -> {
            openDriverDialog();
            return true;
        });
        SwitchPreferenceCompat customVk = requirePreference("zinkPreferSystemDriver", SwitchPreferenceCompat.class);
        boolean gplay = BuildConfig.BUILD_TYPE.equals("gplay");
        boolean isAdreno = GpuUtils.getGlInfo().isAdreno();
        boolean hasSupport = DriverManager.isSupportedByDevice();
        customVk.setVisible(hasSupport && (!gplay || isAdreno));
        customVk.setChecked(LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER);

        computeVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if(activity != null) {
            requirePreference("ignoreNotch").setVisible(LauncherPreferences.hasNotch(activity));
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility(){
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        updateDriversPref();
    }

    private void openDriverDialog(){
        if(mDialogScreen == null) {
            mDialogScreen = new DriverConfigDialog();
            mDialogScreen.prepare(getContext(), mInstallDriver);
            mDialogScreen.onDismiss(this::updateDriversPref);
        }
        mDialogScreen.show();
    }
    private void updateDriversPref(){

        Preference preference = requirePreference("manageDrivers");
        boolean hasSupport = DriverManager.isSupportedByDevice();
        boolean gplay = BuildConfig.BUILD_TYPE.equals("gplay");
        if(LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER || gplay || !hasSupport){
            preference.setVisible(false);
            return;
        }
        Driver driver = DriverManager.getPreferredDriver();
        if(driver instanceof BuiltinDriver)
            preference.setSummary(BuiltinDriver.deviceHasDriver() ? R.string.driver_default_name : R.string.driver_config_pref_subtitle);
        else
            preference.setSummary(driver.getName());
        preference.setVisible(true);
    }

    private void refreshRendererList() {
        RendererCache.invalidate();
        RendererCache list = RendererCache.getCompatibleRenderers(requireContext());
        ListPreference pref = requirePreference("renderer", ListPreference.class);
        pref.setEntries(list.rendererDisplayNames);
        pref.setEntryValues(list.rendererIds.toArray(new String[0]));
        if (!list.rendererIds.contains(pref.getValue())) pref.setValue(list.rendererIds.get(0));
    }

    private void importRenderer(Uri uri) {
        android.content.Context appContext = requireContext().getApplicationContext();
        MrLauncherApplication.sExecutorService.execute(() -> {
            String message;
            ExternalRenderer imported = null;
            try {
                imported = ExternalRendererManager.importFromUri(appContext, uri);
                message = appContext.getString(R.string.renderer_import_ok, imported.name);
            } catch (Exception e) {
                message = appContext.getString(R.string.renderer_import_failed, e.getMessage());
            }
            final String toast = message;
            final ExternalRenderer result = imported;
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                Toast.makeText(activity, toast, Toast.LENGTH_LONG).show();
                refreshRendererList();
                if (result != null) {
                    ListPreference pref = requirePreference("renderer", ListPreference.class);
                    if (pref.findIndexOfValue(result.tag()) >= 0) pref.setValue(result.tag());
                }
            });
        });
    }

    private void showRendererManager() {
        ExternalRendererManager.load(requireContext());
        List<ExternalRenderer> renderers = ExternalRendererManager.all();
        if (renderers.isEmpty()) {
            Toast.makeText(requireContext(), R.string.renderer_manage_empty, Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[renderers.size()];
        for (int i = 0; i < items.length; i++) {
            ExternalRenderer r = renderers.get(i);
            items[i] = r.name + " (" + getString(r.isDeletable() ? R.string.renderer_manage_file : R.string.renderer_manage_plugin) + ")";
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.renderer_manage_title)
                .setItems(items, (dialog, which) -> {
                    ExternalRenderer r = renderers.get(which);
                    if (!r.isDeletable()) {
                        Toast.makeText(requireContext(), R.string.renderer_plugin_hint, Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.renderer_delete_title)
                            .setMessage(getString(R.string.renderer_delete_message, r.name))
                            .setPositiveButton(android.R.string.ok, (d, w) -> {
                                ExternalRendererManager.delete(requireContext(), r);
                                refreshRendererList();
                                Toast.makeText(requireContext(), R.string.renderer_deleted, Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
