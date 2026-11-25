package com.mrlauncher.app.fragments;

import static com.mrlauncher.app.Tools.openPath;
import static com.mrlauncher.app.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcVersionSpinner;

import com.mrlauncher.app.CustomControlsActivity;
import com.mrlauncher.app.R;

import com.mrlauncher.app.Tools;
import com.mrlauncher.app.contracts.OpenDocumentWithExtension;
import com.mrlauncher.app.extra.ExtraConstants;
import com.mrlauncher.app.extra.ExtraCore;
import com.mrlauncher.app.instances.Instance;
import com.mrlauncher.app.instances.Instances;
import com.mrlauncher.app.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

                mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));
        animateEntrance(view);


        mDiscordButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private void animateEntrance(View root) {
        int[] ids = {R.id.social_media_button, R.id.custom_control_button, R.id.install_jar_button, R.id.share_logs_button, R.id.open_files_button};
        for (int i = 0; i < ids.length; i++) {
            View v = root.findViewById(ids[i]);
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(48f);
            v.animate().alpha(1f).translationY(0f).setStartDelay(80L * i).setDuration(380)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        View play = root.findViewById(R.id.play_button);
        if (play != null) {
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(play,
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.035f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.035f));
            pulse.setDuration(1100);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.start();
        }
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        mModInstallerLauncher.launch(null);
    }
}
