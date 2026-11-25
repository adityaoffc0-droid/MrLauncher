package com.mrlauncher.app.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mrlauncher.app.R;
import com.mrlauncher.app.Tools;
import com.mrlauncher.app.extra.ExtraConstants;
import com.mrlauncher.app.extra.ExtraCore;

public class ElyByLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";

    public ElyByLoginFragment() {
        super(R.layout.fragment_elyby_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText user = view.findViewById(R.id.elyby_user);
        EditText pass = view.findViewById(R.id.elyby_pass);
        EditText totp = view.findViewById(R.id.elyby_totp);
        view.findViewById(R.id.elyby_login_button).setOnClickListener(v -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            String t = totp.getText().toString().trim();
            if (u.isEmpty() || p.isEmpty()) {
                Tools.dialog(v.getContext(), getString(R.string.global_error),
                        getString(R.string.elyby_login_username_hint) + " / " + getString(R.string.elyby_login_password_hint));
                return;
            }
            ExtraCore.setValue(ExtraConstants.ELYBY_LOGIN_TODO, u + "\n" + p + "\n" + t);
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        });
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(300).start();
    }
}
