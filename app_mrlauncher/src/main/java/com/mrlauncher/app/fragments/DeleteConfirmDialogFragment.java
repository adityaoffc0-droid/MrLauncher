package com.mrlauncher.app.fragments;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.mrlauncher.app.R;
import com.mrlauncher.app.Tools;
import com.mrlauncher.app.instances.Instance;
import com.mrlauncher.app.instances.Instances;
import com.mrlauncher.app.instances.InstanceIconProvider;
import java.io.IOException;


public class DeleteConfirmDialogFragment extends DialogFragment {
    private final Instance mInstance = Instances.loadSelectedInstance();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (mInstance == null) dismiss();
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.instance_delete)
                .setMessage(R.string.instance_delete_confirmation)
                .setPositiveButton(R.string.global_delete, (dialog, which) -> {
                    if (mInstance == null) return;
                    InstanceIconProvider.dropIcon(mInstance);
                    Tools.removeCurrentFragment(requireActivity());
                    try {
                        Instances.removeInstance(mInstance);
                    } catch (IOException e) {
                        Tools.showErrorRemote(e);
                    }
                })
                .setNegativeButton(R.string.global_no, null)
                .create();
    }
    public static String TAG = "delete_dialog_confirm";
}