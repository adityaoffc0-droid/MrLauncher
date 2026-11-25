package com.mrlauncher.app.game.renderer.ext;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Guesses renderer settings from the library file name when no renderer.json is present
 */
public final class RendererPresets {
    public final String name;
    public final boolean mesa;
    public final int glesVersion;
    public final boolean vulkan;
    public final Map<String, String> env = new HashMap<>();

    private RendererPresets(String name, boolean mesa, int glesVersion, boolean vulkan) {
        this.name = name;
        this.mesa = mesa;
        this.glesVersion = glesVersion;
        this.vulkan = vulkan;
    }

    public static RendererPresets detect(String libraryFileName) {
        String n = libraryFileName.toLowerCase(Locale.ROOT);
        if (n.contains("mobileglues")) return new RendererPresets("MobileGlues", false, 3, false);
        if (n.contains("holygl4es")) return new RendererPresets("Holy GL4ES", false, 3, false);
        if (n.contains("gl4es")) return new RendererPresets("GL4ES", false, 2, false);
        if (n.contains("ltw")) return new RendererPresets("OpenLTW", false, 3, false);
        if (n.contains("zink")) {
            RendererPresets p = new RendererPresets("Zink (Vulkan)", true, 3, true);
            p.env.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
            p.env.put("MESA_GL_VERSION_OVERRIDE", "4.6");
            p.env.put("MESA_GLSL_VERSION_OVERRIDE", "460");
            return p;
        }
        if (n.contains("freedreno") || n.contains("kgsl")) {
            RendererPresets p = new RendererPresets("Freedreno (KGSL)", true, 3, false);
            p.env.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
            return p;
        }
        if (n.contains("mesa")) return new RendererPresets("Mesa", true, 3, false);
        return new RendererPresets(null, false, 3, false);
    }
}
