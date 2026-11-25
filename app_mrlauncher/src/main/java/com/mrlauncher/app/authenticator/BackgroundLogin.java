package com.mrlauncher.app.authenticator;

import androidx.annotation.NonNull;

import com.mrlauncher.app.authenticator.listener.LoginListener;
import com.mrlauncher.app.authenticator.accounts.Account;

public interface BackgroundLogin {
    void createAccount(@NonNull LoginListener loginListener, String code);
    void refreshAccount(@NonNull LoginListener loginListener, Account account);
    interface Creator {
        BackgroundLogin create();
    }
}
