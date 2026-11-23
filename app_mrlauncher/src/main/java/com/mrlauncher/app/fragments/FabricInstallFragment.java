package com.mrlauncher.app.fragments;

import com.mrlauncher.app.modding.fabric.FabriclikeUtils;

public class FabricInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "FabricInstallFragment";

    public FabricInstallFragment() {
        super(FabriclikeUtils.FABRIC_UTILS, TAG);
    }
}
