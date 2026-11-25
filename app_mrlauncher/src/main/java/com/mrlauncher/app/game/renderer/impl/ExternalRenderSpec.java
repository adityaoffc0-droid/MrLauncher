package com.mrlauncher.app.game.renderer.impl;

import android.content.Context;

import com.mrlauncher.app.game.renderer.RenderSpec;
import com.mrlauncher.app.game.renderer.ext.ExternalRenderer;
import com.mrlauncher.app.utils.GpuUtils;
import com.mrlauncher.app.utils.JREUtils;

import java.io.File;
import java.util.Map;

import git.artdeell.mojoexec.MojoExec;

/**
 * RenderSpecs for imported and plugin renderers
 */
public final class ExternalRenderSpec {
    private ExternalRenderSpec() {}

    public static RenderSpec create(ExternalRenderer renderer) {
        return renderer.mesa ? new Mesa(renderer) : new Gles(renderer);
    }

    public static class Gles extends GLESRenderSpec {
        private final ExternalRenderer r;

        public Gles(ExternalRenderer r) {
            this.r = r;
        }
        public boolean coreOnly() {
            return r.coreOnly;
        }
        public boolean compatibleDevice(Context context) {
            return new File(r.libraryPath).isFile()
                    && (r.glesVersion <= 2 || JREUtils.getDetectedVersion() >= r.glesVersion);
        }
        public String name() {
            return r.name;
        }
        public int displayName() {
            return 0;
        }
        public String displayName(Context context) {
            return r.name;
        }
        public String tag() {
            return r.tag();
        }
        public String library() {
            return r.libraryPath;
        }
        public String librarySearchPath() {
            return r.libraryDir;
        }
        protected int glesVersion() {
            return r.glesVersion;
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            super.setupEnvironment(context, envMap);
            envMap.putAll(r.env);
        }
    }

    public static class Mesa extends MesaRenderSpec {
        private final ExternalRenderer r;

        public Mesa(ExternalRenderer r) {
            this.r = r;
        }
        public boolean compatibleDevice(Context context) {
            if (!new File(r.libraryPath).isFile()) return false;
            return !r.vulkan || GpuUtils.checkVulkanSupport(context.getPackageManager());
        }
        public String name() {
            return r.name;
        }
        public int displayName() {
            return 0;
        }
        public String displayName(Context context) {
            return r.name;
        }
        public String tag() {
            return r.tag();
        }
        public String library() {
            return r.libraryPath;
        }
        public String librarySearchPath() {
            return r.libraryDir;
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            super.setupEnvironment(context, envMap);
            envMap.putAll(r.env);
        }
        public boolean setupRenderer() {
            if (r.vulkan) MojoExec.preloadVulkan();
            return MojoExec.prepareEgl(library(), true, false, 0);
        }
    }
}
