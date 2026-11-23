package com.mrlauncher.app.authenticator.impl;

import static com.mrlauncher.app.MrLauncherApplication.sExecutorService;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import com.mrlauncher.app.Tools;
import com.mrlauncher.app.authenticator.AuthType;
import com.mrlauncher.app.authenticator.BackgroundLogin;
import com.mrlauncher.app.authenticator.accounts.Accounts;
import com.mrlauncher.app.authenticator.accounts.Account;
import com.mrlauncher.app.authenticator.listener.LoginListener;
import com.mrlauncher.app.authenticator.model.OAuthTokenResponse;
import com.mrlauncher.app.utils.PresentableException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.Callable;

public class ElyByBackgroundLogin implements BackgroundLogin {
    public static final BackgroundLogin.Creator CREATOR = ElyByBackgroundLogin::new;

    private static final String authTokenUrl = "https://account.ely.by/api/oauth2/v1/token";
    private static final String accountInfoUrl = "https://account.ely.by/api/account/v1/info";
    private static final String YGG_API = "https://authserver.ely.by/auth/";
    /** Marks refreshToken values that hold a Yggdrasil clientToken (accounts without it use the old OAuth flow). */
    private static final String YGG_PREFIX = "ygg:";
    private static final long TOKEN_LIFETIME_MS = 12L * 60 * 60 * 1000;

    private OAuthTokenResponse mOAuthData;
    private ElyAccountInfo mAccountInfo;
    private long mExpiresAt;

    private ElyByBackgroundLogin() {}

    private void acquireAccountDetails(
            @NonNull LoginListener loginListener, Callable<Void> continuation,
            String code, boolean isRefresh
    ) {
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            loginListener.setMaxLoginProgress(2);
            try {
                notifyProgress(loginListener, 1);
                acquireTokens(isRefresh, code);
                notifyProgress(loginListener, 2);
                mAccountInfo = acquireAccountData(mOAuthData.accessToken);
                continuation.call();
            }catch (Exception e){
                Log.e("MicroAuth", "Exception thrown during authentication", e);
                Tools.runOnUiThread(()->loginListener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }

    private void fillAccount(Account acc) {
        acc.expiresAt = mExpiresAt;
        acc.authType = AuthType.ELY_BY;
        acc.accessToken = mOAuthData.accessToken;
        acc.refreshToken = mOAuthData.refreshToken;
        acc.username = mAccountInfo.username;
        acc.profileId = mAccountInfo.uuid;
        acc.xuid = null;
        acc.updateSkinFace();
    }

    private void fillYgg(Account acc, JsonObject res, String clientToken) {
        JsonObject profile = res.getAsJsonObject("selectedProfile");
        if (profile == null) throw new IllegalStateException("This Ely.by account has no player profile");
        acc.authType = AuthType.ELY_BY;
        acc.accessToken = res.get("accessToken").getAsString();
        acc.refreshToken = YGG_PREFIX + clientToken;
        acc.username = profile.get("name").getAsString();
        acc.profileId = profile.get("id").getAsString();
        acc.expiresAt = System.currentTimeMillis() + TOKEN_LIFETIME_MS;
        acc.xuid = null;
        acc.updateSkinFace();
    }

    /** "code" is "username\npassword[\ntotp]". Ely.by expects "password:totp" when 2FA is enabled. */
    @Override
    public void createAccount(@NonNull LoginListener listener, String code) {
        String[] parts = code.split("\n", 3);
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            listener.setMaxLoginProgress(2);
            try {
                notifyProgress(listener, 1);
                String clientToken = UUID.randomUUID().toString().replace("-", "");
                String password = parts.length > 1 ? parts[1] : "";
                if (parts.length > 2 && !parts[2].trim().isEmpty()) password += ":" + parts[2].trim();
                JsonObject body = new JsonObject();
                JsonObject agent = new JsonObject();
                agent.addProperty("name", "Minecraft");
                agent.addProperty("version", 1);
                body.add("agent", agent);
                body.addProperty("username", parts[0]);
                body.addProperty("password", password);
                body.addProperty("clientToken", clientToken);
                body.addProperty("requestUser", false);
                JsonObject res = YggdrasilUtils.post(YGG_API, "authenticate", body);
                notifyProgress(listener, 2);
                Account account = Accounts.create(acc -> fillYgg(acc, res, clientToken));
                Tools.runOnUiThread(() -> listener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("ElyByLogin", "Login failed", e);
                Tools.runOnUiThread(() -> listener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }

    @Override
    public void refreshAccount(@NonNull LoginListener listener, Account account) {
        if (account.refreshToken == null || !account.refreshToken.startsWith(YGG_PREFIX)) {
            // Account created with the old browser (OAuth) login
            acquireAccountDetails(listener, ()->{
                fillAccount(account);
                account.save();
                Tools.runOnUiThread(() -> listener.onLoginDone(account));
                return null;
            }, account.refreshToken, true);
            return;
        }
        String clientToken = account.refreshToken.substring(YGG_PREFIX.length());
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            listener.setMaxLoginProgress(2);
            try {
                notifyProgress(listener, 1);
                JsonObject body = new JsonObject();
                body.addProperty("accessToken", account.accessToken);
                body.addProperty("clientToken", clientToken);
                body.addProperty("requestUser", false);
                JsonObject res = YggdrasilUtils.post(YGG_API, "refresh", body);
                notifyProgress(listener, 2);
                fillYgg(account, res, clientToken);
                account.save();
                Tools.runOnUiThread(() -> listener.onLoginDone(account));
            } catch (Exception e) {
                Log.e("ElyByLogin", "Refresh failed", e);
                Tools.runOnUiThread(() -> listener.onLoginError(e));
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
        });
    }

    private void acquireTokens(boolean isRefresh, String code) throws IOException, PresentableException {
        URL url = new URL(authTokenUrl);
        Log.i("MicrosoftLogin", "isRefresh=" + isRefresh + ", authCode= "+code);

        String formData = CommonLoginUtils.convertToFormData(
                "client_id", "mojolauncher2",
                "client_secret", "o14Zb2Zzj0_k6o4kN0t1mIEhoQxeayn8hYi5VSX2q3NXrdQm5T2Q6wqsCfpv1vhu",
                "redirect_uri", "internalredirect://complete",
                isRefresh ? "refresh_token" : "code", code,
                "grant_type", isRefresh ? "refresh_token" : "authorization_code"
        );
        mOAuthData = CommonLoginUtils.exchangeAuthCode(url, formData);
        mExpiresAt = mOAuthData.expiresIn*1000 + System.currentTimeMillis();
    }

    private ElyAccountInfo acquireAccountData(String accessToken) throws IOException, PresentableException {
        URL url = new URL(accountInfoUrl);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setUseCaches(false);
        conn.connect();
        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                return Tools.GLOBAL_GSON.fromJson(reader, ElyAccountInfo.class);
            } finally {
                conn.disconnect();
            }
        }else{
            throw CommonLoginUtils.getResponseThrowable(conn);
        }
    }

    private void notifyProgress(LoginListener listener, int step) {
        Tools.runOnUiThread(() -> listener.onLoginProgress(step));
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, step*50);
    }

    private static class ElyAccountInfo {
        public String uuid;
        public String username;
    }
}
