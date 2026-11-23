package com.mrlauncher.app.authenticator.impl;

import static com.mrlauncher.app.MrLauncherApplication.sExecutorService;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import com.mrlauncher.app.Tools;
import com.mrlauncher.app.authenticator.AuthType;
import com.mrlauncher.app.authenticator.BackgroundLogin;
import com.mrlauncher.app.authenticator.accounts.Account;
import com.mrlauncher.app.authenticator.accounts.Accounts;
import com.mrlauncher.app.authenticator.listener.LoginListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Yggdrasil (authlib-injector) login for LittleSkin. "code" is "username\npassword" when creating an account. */
public class LittleSkinBackgroundLogin implements BackgroundLogin {
    public static final BackgroundLogin.Creator CREATOR = LittleSkinBackgroundLogin::new;
    private static final String API = "https://littleskin.cn/api/yggdrasil/authserver/";
    private static final long TOKEN_LIFETIME_MS = 12L * 60 * 60 * 1000;

    private LittleSkinBackgroundLogin() {}

    private static JsonObject post(String endpoint, JsonObject body, boolean allowEmpty) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API + endpoint).openConnection();
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
                StringBuilder sb = new StringBuilder(); char[] buf = new char[1024]; int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                text = sb.toString();
            }
        }
        conn.disconnect();
        if (code >= 200 && code < 300) {
            if (text.isEmpty()) return allowEmpty ? new JsonObject() : null;
            return Tools.GLOBAL_GSON.fromJson(text, JsonObject.class);
        }
        String msg = "HTTP " + code;
        try {
            JsonObject err = Tools.GLOBAL_GSON.fromJson(text, JsonObject.class);
            if (err != null && err.has("errorMessage")) msg = err.get("errorMessage").getAsString();
        } catch (Exception ignored) {}
        throw new IOException(msg);
    }

    private void fill(Account acc, JsonObject res, String clientToken) {
        JsonObject profile = res.getAsJsonObject("selectedProfile");
        if (profile == null) throw new IllegalStateException("This LittleSkin account has no player profile");
        acc.authType = AuthType.LITTLE_SKIN;
        acc.accessToken = res.get("accessToken").getAsString();
        acc.refreshToken = clientToken; // clientToken is kept here
        acc.username = profile.get("name").getAsString();
        acc.profileId = profile.get("id").getAsString();
        acc.expiresAt = System.currentTimeMillis() + TOKEN_LIFETIME_MS;
        acc.xuid = null;
        acc.updateSkinFace();
    }

    @Override
    public void createAccount(@NonNull LoginListener listener, String code) {
        String[] parts = code.split("\n", 2);
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            listener.setMaxLoginProgress(2);
            try {
                Tools.runOnUiThread(() -> listener.onLoginProgress(1));
                ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 50);
                String clientToken = UUID.randomUUID().toString().replace("-", "");
                JsonObject body = new JsonObject();
                JsonObject agent = new JsonObject();
                agent.addProperty("name", "Minecraft"); agent.addProperty("version", 1);
                body.add("agent", agent);
                body.addProperty("username", parts[0]);
                body.addProperty("password", parts.length > 1 ? parts[1] : "");
                body.addProperty("clientToken", clientToken);
                body.addProperty("requestUser", false);
                JsonObject res = post("authenticate", body, false);
                Tools.runOnUiThread(() -> listener.onLoginProgress(2));
                Account account = Accounts.create(acc -> fill(acc, res, clientToken));
                Tools.runOnUiThread(() -> listener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("LittleSkin", "Login failed", e);
                Tools.runOnUiThread(() -> listener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }

    @Override
    public void refreshAccount(@NonNull LoginListener listener, Account account) {
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            listener.setMaxLoginProgress(2);
            try {
                Tools.runOnUiThread(() -> listener.onLoginProgress(1));
                JsonObject body = new JsonObject();
                body.addProperty("accessToken", account.accessToken);
                body.addProperty("clientToken", account.refreshToken);
                JsonObject res = post("refresh", body, false);
                Tools.runOnUiThread(() -> listener.onLoginProgress(2));
                fill(account, res, account.refreshToken);
                account.save();
                Tools.runOnUiThread(() -> listener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("LittleSkin", "Refresh failed", e);
                Tools.runOnUiThread(() -> listener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }
}
