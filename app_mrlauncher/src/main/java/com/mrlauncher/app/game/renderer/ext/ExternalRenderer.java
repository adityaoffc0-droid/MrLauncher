package com.mrlauncher.app.game.renderer.ext;

import java.io.File;
import java.util.Map;

/**
 * Renderer that is not bundled with the launcher: imported from a file or provided by a plugin APK
 */
public final class ExternalRenderer {
    public static final int SOURCE_FILE = 0;
    public static final int SOURCE_PLUGIN = 1;

    public final String id;
    public final String name;
    public final String libraryPath;
    public final String libraryDir;
    public final boolean mesa;
    public final int glesVersion;
    public final boolean vulkan;
    public final Map<String, String> env;
    public final int source;
    public final String packageName;
    public final File root;
    public final boolean coreOnly;

    public ExternalRenderer(String id, String name, File library, boolean mesa, int glesVersion,
                            boolean vulkan, Map<String, String> env, int source,
                            String packageName, File root) {
        this.id = id;
        this.name = name;
        this.libraryPath = library.getAbsolutePath();
        this.libraryDir = library.getParentFile().getAbsolutePath();
        this.mesa = mesa;
        this.glesVersion = glesVersion;
        this.vulkan = vulkan;
        this.env = env;
        this.source = source;
        this.packageName = packageName;
        this.root = root;
        String lib = library.getName().toLowerCase(java.util.Locale.ROOT);
        this.coreOnly = lib.contains("mobileglues") || lib.contains("ltw");
    }

    public boolean isGl4es() {
        return libraryPath.toLowerCase(java.util.Locale.ROOT).contains("gl4es");
    }

    public String tag() {
        return ExternalRendererManager.TAG_PREFIX + id;
    }

    public boolean isDeletable() {
        return source == SOURCE_FILE;
    }
}
