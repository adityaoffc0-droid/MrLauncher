package com.mrlauncher.app.fragments;

import com.mrlauncher.app.modding.fabric.FabriclikeUtils;
import com.mrlauncher.app.modding.ModloaderListenerProxy;

public class QuiltInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "QuiltInstallFragment";
    private static ModloaderListenerProxy sTaskProxy;

    public QuiltInstallFragment() {
        super(FabriclikeUtils.QUILT_UTILS, TAG);
    }
}
