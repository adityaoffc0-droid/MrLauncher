package com.mrlauncher.app.authenticator.impl;

import com.google.gson.JsonObject;
import com.mrlauncher.app.Tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal Yggdrasil authserver client shared by the in-app (username/password) logins. */
public class YggdrasilUtils {
    public static JsonObject post(String api, String endpoint, JsonObject body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(api + endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) { os.write(data); }
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = "";
        if (is != null) {
            try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                text = sb.toString();
            }
        }
        conn.disconnect();
        if (code >= 200 && code < 300) {
            if (text.isEmpty()) return new JsonObject();
            return Tools.GLOBAL_GSON.fromJson(text, JsonObject.class);
        }
        String msg = "HTTP " + code;
        try {
            JsonObject err = Tools.GLOBAL_GSON.fromJson(text, JsonObject.class);
            if (err != null && err.has("errorMessage")) msg = err.get("errorMessage").getAsString();
        } catch (Exception ignored) {}
        throw new IOException(msg);
    }
}
