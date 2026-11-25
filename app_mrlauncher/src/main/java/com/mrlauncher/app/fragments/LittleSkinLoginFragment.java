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

public class LittleSkinLoginFragment extends Fragment {
    public static final String TAG = "LITTLESKIN_LOGIN_FRAGMENT";

    public LittleSkinLoginFragment() {
        super(R.layout.fragment_littleskin_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText user = view.findViewById(R.id.littleskin_user);
        EditText pass = view.findViewById(R.id.littleskin_pass);
        view.findViewById(R.id.littleskin_login_button).setOnClickListener(v -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            if (u.isEmpty() || p.isEmpty()) {
                Tools.dialog(v.getContext(), getString(R.string.global_error), getString(R.string.littleskin_login_username_hint) + " / " + getString(R.string.littleskin_login_password_hint));
                return;
            }
            ExtraCore.setValue(ExtraConstants.LITTLESKIN_LOGIN_TODO, u + "\n" + p);
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        });
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(300).start();
    }
}
