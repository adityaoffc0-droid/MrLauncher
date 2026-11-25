package com.mrlauncher.app.authenticator;

import com.google.gson.annotations.SerializedName;

import com.mrlauncher.app.authenticator.impl.ElyByBackgroundLogin;
import com.mrlauncher.app.authenticator.impl.LittleSkinBackgroundLogin;
import com.mrlauncher.app.authenticator.impl.MicrosoftBackgroundLogin;

import com.mrlauncher.app.R;

public enum AuthType {
    @SerializedName("microsoft")
    MICROSOFT(
            MicrosoftBackgroundLogin.CREATOR,
            R.drawable.ic_auth_ms,
            null,
            "https://mineskin.eu/skin/%s" // Switched from mc-heads.net cause blocked in Russia
    ),
    @SerializedName("elyby")
    ELY_BY(
            ElyByBackgroundLogin.CREATOR,
            R.drawable.ic_auth_elyby,
            "ely.by",
            "http://skinsystem.ely.by/skins/%s.png"
    ),
    @SerializedName("littleskin")
    LITTLE_SKIN(
            LittleSkinBackgroundLogin.CREATOR,
            R.drawable.ic_auth_littleskin,
            "littleskin.cn",
            "https://littleskin.cn/skin/%s.png"
    ),
    @SerializedName("local")
    LOCAL(null, 0, null, null);

    private final BackgroundLogin.Creator mCreator;
    public final int iconResource;
    public final String injectorUrl;
    public final String skinUrl;

    AuthType(BackgroundLogin.Creator creator, int iconResource, String injectorUrl, String skinUrl) {
        this.mCreator = creator;
        this.iconResource = iconResource;
        this.injectorUrl = injectorUrl;
        this.skinUrl = skinUrl;
    }

    public boolean requiresLogin() {
        return mCreator != null;
    }

    public BackgroundLogin createAuth() {
        if(mCreator == null) throw new RuntimeException("This account does not support login");
        return mCreator.create();
    }
}
