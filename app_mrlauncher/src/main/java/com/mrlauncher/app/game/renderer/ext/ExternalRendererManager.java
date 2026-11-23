package com.mrlauncher.app.game.renderer.ext;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;

import com.mrlauncher.app.Architecture;
import com.mrlauncher.app.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Keeps track of renderers imported from .so/.zip files and renderers provided by plugin APKs.
 *
 * A plugin APK is detected by an activity with the {@link #ACTION_PLUGIN} intent filter and
 * these application level meta-data entries:
 * <ul>
 *     <li>mrlauncher.renderer.library (required): library file name inside the APK's native lib dir</li>
 *     <li>mrlauncher.renderer.name: display name</li>
 *     <li>mrlauncher.renderer.type: "gles" (default) or "mesa"</li>
 *     <li>mrlauncher.renderer.gles: OpenGL ES version for the "gles" type (default 3)</li>
 *     <li>mrlauncher.renderer.vulkan: true if the renderer needs Vulkan</li>
 *     <li>mrlauncher.renderer.env: environment as KEY=VALUE pairs separated by ';'</li>
 * </ul>
 * The Zalith/FCL plugin format is detected as well, see {@link #discoverZalithPlugins}.
 * The plugin must keep android:extractNativeLibs="true" so the library exists on the disk.
 */
public final class ExternalRendererManager {
    private static final String TAG = "ExtRenderers";
    public static final String TAG_PREFIX = "ext:";
    public static final String ACTION_PLUGIN = "com.mrlauncher.app.RENDERER_PLUGIN";
    private static final String META = "mrlauncher.renderer.";
    private static final String DIR_NAME = "renderers";
    private static final String MANIFEST = "renderer.json";
    private static final long MAX_IMPORT_BYTES = 600L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 500;

    private static final Map<String, ExternalRenderer> sRenderers = new LinkedHashMap<>();

    private ExternalRendererManager() {}

    public static synchronized ExternalRenderer get(String tag) {
        if (tag == null || !tag.startsWith(TAG_PREFIX)) return null;
        return sRenderers.get(tag.substring(TAG_PREFIX.length()));
    }

    public static synchronized List<ExternalRenderer> all() {
        return Collections.unmodifiableList(new ArrayList<>(sRenderers.values()));
    }

    /**
     * Rescan the import directory and the installed plugins
     */
    public static synchronized void load(Context context) {
        sRenderers.clear();
        File base = new File(context.getFilesDir(), DIR_NAME);
        File[] dirs = base.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (!dir.isDirectory()) continue;
                try {
                    ExternalRenderer r = readFromDirectory(dir);
                    sRenderers.put(r.id, r);
                } catch (IOException e) {
                    Log.w(TAG, "Skipping broken renderer " + dir.getName() + ": " + e.getMessage());
                }
            }
        }
        discoverPlugins(context);
        discoverZalithPlugins(context);
    }

    @SuppressWarnings("deprecation")
    private static void discoverPlugins(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(new Intent(ACTION_PLUGIN), PackageManager.GET_META_DATA);
        } catch (Exception e) {
            Log.w(TAG, "Plugin query failed", e);
            return;
        }
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            ApplicationInfo app = info.activityInfo.applicationInfo;
            Bundle meta = new Bundle();
            if (app.metaData != null) meta.putAll(app.metaData);
            if (info.activityInfo.metaData != null) meta.putAll(info.activityInfo.metaData);
            String libName = meta.getString(META + "library");
            if (libName == null || libName.contains("/") || libName.contains("..")) continue;
            File lib = new File(app.nativeLibraryDir, libName);
            if (!lib.isFile()) {
                Log.w(TAG, app.packageName + " declares " + libName + " but it is not extracted");
                continue;
            }
            RendererPresets preset = RendererPresets.detect(libName);
            boolean mesa = meta.containsKey(META + "type")
                    ? "mesa".equalsIgnoreCase(meta.getString(META + "type")) : preset.mesa;
            int gles = meta.getInt(META + "gles", preset.glesVersion);
            boolean vulkan = meta.getBoolean(META + "vulkan", preset.vulkan);
            String name = meta.getString(META + "name");
            if (name == null) name = preset.name != null ? preset.name : app.loadLabel(pm).toString();
            Map<String, String> env = new HashMap<>(preset.env);
            parseEnv(meta.getString(META + "env"), env);
            String id = "plugin_" + sanitize(app.packageName);
            sRenderers.put(id, new ExternalRenderer(id, name, lib, mesa, gles, vulkan, env,
                    ExternalRenderer.SOURCE_PLUGIN, app.packageName, null));
        }
    }

    /**
     * Detects renderer plugin APKs in the Zalith/FCL format: application meta-data fclPlugin=true,
     * renderer="Name:glLibrary:eglLibrary" and optional pojavEnv="KEY=VALUE:KEY2=VALUE2"
     */
    @SuppressWarnings("deprecation")
    private static void discoverZalithPlugins(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (Exception e) {
            Log.w(TAG, "Installed applications query failed", e);
            return;
        }
        for (ApplicationInfo app : apps) {
            Bundle meta = app.metaData;
            if (meta == null || !"true".equalsIgnoreCase(String.valueOf(meta.get("fclPlugin")))) continue;
            String spec = meta.getString("renderer");
            if (spec == null) continue;
            String[] parts = spec.split(":");
            if (parts.length < 2) continue;
            String label = parts[0].trim();
            String glName = parts[1].trim();
            String eglName = parts.length > 2 ? parts[2].trim() : "libEGL.so";
            String lower = eglName.toLowerCase(Locale.ROOT);
            boolean mesa = lower.contains("mesa") || lower.contains("zink");
            String libName = mesa ? eglName : glName;
            if (libName.isEmpty() || libName.contains("/") || libName.contains("..")) continue;
            File lib = new File(app.nativeLibraryDir, libName);
            if (!lib.isFile()) {
                Log.w(TAG, app.packageName + " declares " + libName + " but it is not extracted");
                continue;
            }
            String id = "plugin_" + sanitize(app.packageName);
            if (sRenderers.containsKey(id)) continue;
            RendererPresets preset = RendererPresets.detect(glName + eglName + label);
            Map<String, String> env = new HashMap<>(preset.env);
            parseZalithEnv(meta.getString("pojavEnv"), env);
            sRenderers.put(id, new ExternalRenderer(id, label.isEmpty() ? app.packageName : label, lib,
                    mesa, preset.glesVersion, mesa && lower.contains("zink") || preset.vulkan, env,
                    ExternalRenderer.SOURCE_PLUGIN, app.packageName, null));
        }
    }

    private static void parseZalithEnv(String raw, Map<String, String> out) {
        if (raw == null) return;
        String lastKey = null;
        for (String token : raw.split(":")) {
            int eq = token.indexOf('=');
            if (eq > 0) {
                lastKey = token.substring(0, eq).trim();
                out.put(lastKey, token.substring(eq + 1).trim());
            } else if (lastKey != null) {
                out.put(lastKey, out.get(lastKey) + ":" + token);
            }
        }
    }

    private static void parseEnv(String raw, Map<String, String> out) {
        if (raw == null) return;
        for (String pair : raw.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) out.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
    }

    /**
     * Import a renderer from a raw .so library or a .zip package (optionally with renderer.json)
     *
     * @throws IOException with a user readable message if the file is not usable
     */
    public static ExternalRenderer importFromUri(Context context, Uri uri) throws IOException {
        String display = queryDisplayName(context, uri);
        File tmp = new File(context.getCacheDir(), "renderer_import.tmp");
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) throw new IOException(context.getString(R.string.renderer_import_bad_file));
                copyLimited(in, out);
            }
            boolean zip = hasMagic(tmp, new byte[]{'P', 'K'});
            boolean elf = hasMagic(tmp, new byte[]{0x7f, 'E', 'L', 'F'});
            if (!zip && !elf) throw new IOException(context.getString(R.string.renderer_import_bad_file));

            String baseName = display;
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) baseName = baseName.substring(0, dot);
            String id = sanitize(baseName);
            File root = new File(new File(context.getFilesDir(), DIR_NAME), id);
            deleteRecursive(root);
            if (!root.mkdirs()) throw new IOException("Cannot create " + root);
            try {
                if (zip) {
                    extractZip(context, tmp, root);
                } else {
                    String libName = display.toLowerCase(Locale.ROOT).endsWith(".so") ? sanitizeFileName(display) : id + ".so";
                    copyFile(tmp, new File(root, libName));
                }
                ExternalRenderer r = readFromDirectory(root);
                synchronized (ExternalRendererManager.class) {
                    sRenderers.put(r.id, r);
                }
                return r;
            } catch (IOException | RuntimeException e) {
                deleteRecursive(root);
                throw e;
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    public static synchronized boolean delete(Context context, ExternalRenderer renderer) {
        if (!renderer.isDeletable()) return false;
        deleteRecursive(renderer.root);
        sRenderers.remove(renderer.id);
        return true;
    }

    private static ExternalRenderer readFromDirectory(File root) throws IOException {
        String id = root.getName();
        String name = null;
        String libName = null;
        RendererPresets preset = null;
        Boolean mesaOverride = null;
        Integer glesOverride = null;
        Boolean vulkanOverride = null;
        Map<String, String> envOverride = new HashMap<>();

        File manifest = new File(root, MANIFEST);
        if (manifest.isFile()) {
            try {
                JSONObject json = new JSONObject(readText(manifest));
                name = json.optString("name", null);
                libName = json.optString("library", null);
                if (json.has("type")) mesaOverride = "mesa".equalsIgnoreCase(json.getString("type"));
                if (json.has("gles")) glesOverride = json.getInt("gles");
                if (json.has("vulkan")) vulkanOverride = json.getBoolean("vulkan");
                JSONObject env = json.optJSONObject("env");
                if (env != null) {
                    Iterator<String> keys = env.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        envOverride.put(k, env.getString(k));
                    }
                }
            } catch (JSONException e) {
                throw new IOException("renderer.json: " + e.getMessage());
            }
        }

        File library = null;
        if (libName != null) {
            library = new File(root, libName);
            if (!isInside(root, library)) throw new IOException("Library path escapes the renderer directory");
        } else {
            library = findLibrary(root, 0);
        }
        if (library == null || !library.isFile()) throw new IOException("No .so library found in the package");
        checkElf(library);

        preset = RendererPresets.detect(library.getName());
        Map<String, String> env = new HashMap<>(preset.env);
        env.putAll(envOverride);
        if (name == null) name = preset.name != null ? preset.name : id;
        return new ExternalRenderer(id, name, library,
                mesaOverride != null ? mesaOverride : preset.mesa,
                glesOverride != null ? glesOverride : preset.glesVersion,
                vulkanOverride != null ? vulkanOverride : preset.vulkan,
                env, ExternalRenderer.SOURCE_FILE, null, root);
    }

    private static File findLibrary(File dir, int depth) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        File fallback = null;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".so")) {
                if (RendererPresets.detect(f.getName()).name != null) return f;
                if (fallback == null) fallback = f;
            }
        }
        if (fallback != null) return fallback;
        if (depth < 2) {
            for (File f : files) {
                if (f.isDirectory()) {
                    File r = findLibrary(f, depth + 1);
                    if (r != null) return r;
                }
            }
        }
        return null;
    }

    private static void checkElf(File lib) throws IOException {
        byte[] head = new byte[5];
        try (FileInputStream in = new FileInputStream(lib)) {
            if (in.read(head) < 5) throw new IOException("Library is too short");
        }
        if (head[0] != 0x7f || head[1] != 'E' || head[2] != 'L' || head[3] != 'F')
            throw new IOException(lib.getName() + " is not an ELF library");
        boolean lib64 = head[4] == 2;
        if (lib64 != Architecture.is64BitsDevice())
            throw new IOException(lib.getName() + (lib64 ? " is 64-bit but the launcher runs in 32-bit mode"
                    : " is 32-bit but the launcher runs in 64-bit mode"));
    }

    private static void extractZip(Context context, File zipFile, File root) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            if (zip.size() > MAX_ZIP_ENTRIES) throw new IOException("Too many files in the archive");
            String abi = pickAbi(zip);
            long total = 0;
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String path = entry.getName().replace('\\', '/');
                String stripped = stripAbi(path, abi);
                if (stripped == null) continue;
                File target = new File(root, stripped);
                if (!isInside(root, target)) throw new IOException("Unsafe path in archive: " + path);
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                    throw new IOException("Cannot create " + parent);
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_IMPORT_BYTES) throw new IOException("Archive is too large");
                        out.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private static String pickAbi(ZipFile zip) {
        for (String abi : Build.SUPPORTED_ABIS) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String n = entries.nextElement().getName().replace('\\', '/');
                if (n.startsWith(abi + "/") || n.startsWith("lib/" + abi + "/")) return abi;
            }
        }
        return null;
    }

    /**
     * @return path relative to the renderer root, or null if the entry belongs to another ABI
     */
    private static String stripAbi(String path, String chosenAbi) {
        String[] known = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};
        String p = path.startsWith("lib/") ? path.substring(4) : path;
        for (String abi : known) {
            if (p.startsWith(abi + "/")) {
                return abi.equals(chosenAbi) ? p.substring(abi.length() + 1) : null;
            }
        }
        return path;
    }

    private static boolean isInside(File root, File file) throws IOException {
        String r = root.getCanonicalPath() + File.separator;
        return file.getCanonicalPath().startsWith(r);
    }

    private static String queryDisplayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last != null ? last : "renderer";
    }

    private static String sanitize(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (r.startsWith(".")) r = "_" + r.substring(1);
        return r.isEmpty() ? "renderer" : r;
    }

    private static String sanitizeFileName(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void copyLimited(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_IMPORT_BYTES) throw new IOException("File is too large");
            out.write(buf, 0, n);
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            copyLimited(in, out);
        }
    }

    private static boolean hasMagic(File f, byte[] magic) throws IOException {
        byte[] head = new byte[magic.length];
        try (FileInputStream in = new FileInputStream(f)) {
            if (in.read(head) < magic.length) return false;
        }
        for (int i = 0; i < magic.length; i++) if (head[i] != magic[i]) return false;
        return true;
    }

    private static String readText(File f) throws IOException {
        if (f.length() > 256 * 1024) throw new IOException("renderer.json is too large");
        try (FileInputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
