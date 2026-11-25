package com.mrlauncher.app.modding.modpacks.api.modloader;

import com.mrlauncher.app.instances.InstanceInstaller;

import java.io.IOException;

public interface LoaderInstaller {
    boolean requiresGuiInstallation();
    InstanceInstaller createInstaller() throws IOException;
    String installHeadlessly() throws IOException;
}
