package com.mrlauncher.app.game.renderer;

import static com.mrlauncher.app.game.renderer.def.Renderers.FREEDRENO_RENDERER;
import static com.mrlauncher.app.game.renderer.def.Renderers.GL4ES_RENDERER;
import static com.mrlauncher.app.game.renderer.def.Renderers.LEGACYZINK_RENDERER;
import static com.mrlauncher.app.game.renderer.def.Renderers.LTW_RENDERER;
import static com.mrlauncher.app.game.renderer.def.Renderers.MESA_RENDERER;
import static com.mrlauncher.app.game.renderer.def.Renderers.MESA_RENDERER_EXT;
import static com.mrlauncher.app.game.renderer.def.Renderers.MOBILEGLUES_RENDERER;
import static com.mrlauncher.app.game.renderer.def.Renderers.ZINK_RENDERER;

import android.content.Context;
import com.mrlauncher.app.game.renderer.ext.ExternalRenderer;
import com.mrlauncher.app.game.renderer.ext.ExternalRendererManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatible renderers cache. Used for the UI renderer list
 */
public class RendererCache {
    private static RendererCache sCompatibleRenderers;

    public final List<String> rendererIds;
    public final String[] rendererDisplayNames;

    public RendererCache(List<String> rendererIds, String[] rendererDisplayNames) {
        this.rendererIds = rendererIds;
        this.rendererDisplayNames = rendererDisplayNames;
    }

    /**
     * Return a list of renderers compatible with the current device
     * Don't forget to clean the cache when the list isn't needed anymore
     *
     * @param context application context
     * @return RenderersList containing all compatible renderers
     */
    public static RendererCache getCompatibleRenderers(Context context) {
        if (sCompatibleRenderers != null) return sCompatibleRenderers;
        ExternalRendererManager.load(context);
        // This is the list that controls em all!
        List<String> renderers = new ArrayList<>(java.util.Arrays.asList(
                GL4ES_RENDERER, LTW_RENDERER, MOBILEGLUES_RENDERER, ZINK_RENDERER, FREEDRENO_RENDERER, MESA_RENDERER, MESA_RENDERER_EXT, LEGACYZINK_RENDERER
        ));
        for (ExternalRenderer external : ExternalRendererManager.all()) renderers.add(external.tag());
        ArrayList<String> rendererIds = new ArrayList<>(renderers.size());
        ArrayList<String> rendererNames = new ArrayList<>(rendererIds);
        for (String renderer : renderers) {
            RenderSpec r = GameRenderer.getKnownRenderer(renderer);
            if (r == null) continue;
            if (!r.compatibleDevice(context)) continue;
            rendererIds.add(renderer);
            rendererNames.add(r.displayName(context));
        }
        rendererIds.trimToSize();
        rendererNames.trimToSize();
        return (sCompatibleRenderers = new RendererCache(rendererIds, rendererNames.toArray(new String[0])));
    }

    /**
     * Destroy compatible renderers cache
     */
    public static void releaseRendererCache() {
        if (sCompatibleRenderers == null) return;
        sCompatibleRenderers.rendererIds.clear();
        sCompatibleRenderers = null;
    }

    /**
     * Drop the cache so the next {@link #getCompatibleRenderers(Context)} rescans imported renderers and plugins
     */
    public static void invalidate() {
        sCompatibleRenderers = null;
    }
}
