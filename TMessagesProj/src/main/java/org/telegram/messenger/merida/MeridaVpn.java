package org.telegram.messenger.merida;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.settings.MeridaSettings;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import go.Seq;
import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

/**
 * Built-in Merida VPN: Telegram connects through the owner's Merida VPN server with no setup.
 * <p>
 * On first launch the phone registers with the Merida VPN server like the Merida VPN app does and gets
 * a trial with two keys (Reality and the XHTTP fallback). The Xray core runs inside MeridaGram and
 * exposes a local SOCKS proxy that only Telegram uses; the rest of the phone is not affected.
 * A proxy the user sets up in Telegram's own settings takes priority.
 */
public final class MeridaVpn {

    private static final String API_URL = "https://195.58.56.41:8443";
    /** The server is recognised by its certificate (same as API_PIN in the Merida VPN app). */
    private static final List<String> API_PINS = Arrays.asList(
            "o4FTxwUo8fS/tOP475up1NQlFrTeMkSh+N/yT4aXBDk=",
            "o4FTxwUo8fS/t0P475up1NQlFrTeMkSh+N/yT4aXBDk="
    );
    public static final String BOT_USERNAME = "MeridaVpn_Bot";
    private static final String TEST_URL = "https://www.gstatic.com/generate_204";
    private static final long REFRESH_HOURS = 6;

    public static final int STATE_OFF = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_FAILED = 3;

    public interface Listener {
        void onMeridaVpnChanged();
    }

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "MeridaVpn"));
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private static SharedPreferences preferences;
    private static boolean initialized;
    private static boolean coreEnvReady;
    private static CoreController controller;
    private static volatile boolean running;
    private static volatile int state = STATE_OFF;
    private static volatile String error;
    private static int retryDelaySeconds;

    private MeridaVpn() {
    }

    /** Called once when the app starts. */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        executor.scheduleWithFixedDelay(MeridaVpn::refreshNow, REFRESH_HOURS, REFRESH_HOURS, TimeUnit.HOURS);
        if (MeridaSettings.vpnEnabled.get()) {
            executor.execute(() -> {
                if (!TextUtils.isEmpty(prefs().getString("key", null))) {
                    connect();
                }
                refreshNow();
            });
        }
    }

    /** Turns routing Telegram through Merida VPN on or off (MeridaGram Settings). */
    public static void setEnabled(boolean enabled) {
        MeridaSettings.vpnEnabled.set(enabled);
        executor.execute(() -> {
            if (enabled) {
                connect();
                refreshNow();
            } else {
                stopCore();
                state = STATE_OFF;
                applyToTelegram();
                notifyChanged();
            }
        });
    }

    /** Asks the server for fresh keys and subscription info. */
    public static void refresh() {
        executor.execute(MeridaVpn::refreshNow);
    }

    /** True while Telegram should use the local SOCKS proxy of the Xray core. */
    public static boolean isEnabled() {
        return running && MeridaSettings.vpnEnabled.get();
    }

    public static int getPort() {
        return prefs().getInt("socks_port", 0);
    }

    public static String getUser() {
        return prefs().getString("socks_user", "");
    }

    public static String getPassword() {
        return prefs().getString("socks_pass", "");
    }

    public static int getState() {
        return state;
    }

    public static String getError() {
        return error;
    }

    public static boolean hasAccount() {
        return prefs().contains("expires_at");
    }

    public static boolean isPaid() {
        return "paid".equals(prefs().getString("plan", ""));
    }

    public static boolean isActive() {
        return prefs().getBoolean("active", false);
    }

    /** Subscription end, in milliseconds. */
    public static long getExpiresAt() {
        return prefs().getLong("expires_at", 0) * 1000L;
    }

    public static boolean isUsingFallback() {
        return prefs().getBoolean("fallback", false);
    }

    /** Account number shown in the bot, used to renew from Telegram. */
    public static String getAccountCode() {
        return prefs().getString("code", "");
    }

    public static void addListener(Listener listener) {
        listeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private static SharedPreferences prefs() {
        if (preferences == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("meridavpn", Context.MODE_PRIVATE);
        }
        return preferences;
    }

    private static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener listener : listeners) {
                listener.onMeridaVpnChanged();
            }
        });
    }

    // ---------- Merida VPN server ----------

    private static void refreshNow() {
        if (!MeridaSettings.vpnEnabled.get()) {
            return;
        }
        try {
            final JSONObject account = apiPost("/api/v1/account", new JSONObject().put("device", deviceId()));
            final SharedPreferences prefs = prefs();
            final String oldKey = prefs.getString("key", "");
            final String oldKey2 = prefs.getString("key2", "");
            final boolean wasActive = prefs.getBoolean("active", false);
            final String key = account.optString("key", "");
            final String key2 = account.isNull("key2") ? "" : account.optString("key2", "");
            prefs.edit()
                    .putString("key", key)
                    .putString("key2", key2)
                    .putString("plan", account.optString("plan", "trial"))
                    .putLong("expires_at", account.optLong("expires_at", 0))
                    .putBoolean("active", account.optBoolean("active", false))
                    .putString("code", account.optString("code", ""))
                    .apply();
            error = null;
            if (!key.equals(oldKey) || !key2.equals(oldKey2) || wasActive != isActive() || !running) {
                connect();
            } else {
                notifyChanged();
            }
        } catch (Exception e) {
            FileLog.e(e);
            error = e instanceof ApiException ? e.getMessage() : null;
            if (!running) {
                state = STATE_FAILED;
            }
            notifyChanged();
        }
    }

    @SuppressLint("HardwareIds")
    private static String deviceId() throws Exception {
        String id = prefs().getString("device", null);
        if (id == null) {
            final Context context = ApplicationLoader.applicationContext;
            String android = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (TextUtils.isEmpty(android)) {
                android = UUID.randomUUID().toString();
            }
            id = "a" + sha256Hex(android + context.getPackageName()).substring(0, 32);
            prefs().edit().putString("device", id).apply();
        }
        return id;
    }

    private static JSONObject apiPost(String path, JSONObject body) throws Exception {
        final HttpsURLConnection connection = (HttpsURLConnection) new URL(API_URL + path).openConnection();
        try {
            connection.setSSLSocketFactory(pinnedContext().getSocketFactory());
            // the server is already recognised by its certificate; it has no domain name
            connection.setHostnameVerifier((hostname, session) -> true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            final int code = connection.getResponseCode();
            final InputStream in = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            final String text = in == null ? "" : readAll(in);
            final JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code >= 400) {
                throw new ApiException(json.optString("error", "HTTP " + code));
            }
            return json;
        } finally {
            connection.disconnect();
        }
    }

    private static SSLContext pinnedContext() throws Exception {
        final X509TrustManager trust = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException();
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try {
                    final String hash = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()), Base64.NO_WRAP);
                    if (!API_PINS.contains(hash)) {
                        throw new CertificateException("Merida VPN server certificate does not match");
                    }
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new CertificateException(e);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trust}, null);
        return context;
    }

    private static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }

    // ---------- Xray core ----------

    /** Starts the core with the key that works (main first, then the fallback) and points Telegram at it. */
    private static void connect() {
        if (!MeridaSettings.vpnEnabled.get()) {
            return;
        }
        final SharedPreferences prefs = prefs();
        final String key = prefs.getString("key", "");
        final String key2 = prefs.getString("key2", "");
        if (key.isEmpty()) {
            return;
        }
        if (hasAccount() && !isActive()) {
            // the server has switched the key off: let Telegram connect on its own instead of a dead tunnel
            stopCore();
            applyToTelegram();
            state = STATE_FAILED;
            notifyChanged();
            return;
        }
        state = STATE_CONNECTING;
        notifyChanged();

        final boolean fallbackFirst = prefs.getBoolean("fallback", false) && !key2.isEmpty();
        final String[] order = fallbackFirst ? new String[]{key2, key} : new String[]{key, key2};
        for (String candidate : order) {
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                startCore(candidate);
                applyToTelegram();
                if (controller.measureDelay(TEST_URL) > 0) {
                    prefs.edit().putBoolean("fallback", candidate.equals(key2)).apply();
                    retryDelaySeconds = 0;
                    state = STATE_CONNECTED;
                    notifyChanged();
                    return;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        // neither key answered: let Telegram connect on its own for now and try again later (1, 2, 4… up to 15 min)
        stopCore();
        applyToTelegram();
        state = STATE_FAILED;
        notifyChanged();
        retryDelaySeconds = retryDelaySeconds == 0 ? 60 : Math.min(retryDelaySeconds * 2, 15 * 60);
        executor.schedule(MeridaVpn::connectIfIdle, retryDelaySeconds, TimeUnit.SECONDS);
    }

    private static void connectIfIdle() {
        if (!running && state == STATE_FAILED) {
            connect();
        }
    }

    private static void startCore(String key) throws Exception {
        stopCore();
        final Context context = ApplicationLoader.applicationContext;
        if (!coreEnvReady) {
            Seq.setContext(context);
            Libv2ray.initCoreEnv(context.getDir("xray", Context.MODE_PRIVATE).getAbsolutePath(), "");
            coreEnvReady = true;
        }
        ensureSocksAccount(false);
        controller = Libv2ray.newCoreController(new CoreCallbackHandler() {
            @Override
            public long onEmitStatus(long code, String message) {
                return 0;
            }

            @Override
            public long shutdown() {
                return 0;
            }

            @Override
            public long startup() {
                return 0;
            }
        });
        try {
            controller.startLoop(XrayConfig.build(key, getPort(), getUser(), getPassword()), 0);
        } catch (Exception e) {
            // most likely the port was taken by another app: take a new one and try once more
            ensureSocksAccount(true);
            controller.startLoop(XrayConfig.build(key, getPort(), getUser(), getPassword()), 0);
        }
        running = controller.getIsRunning();
        if (!running) {
            throw new IllegalStateException("Xray core did not start");
        }
    }

    private static void stopCore() {
        running = false;
        if (controller != null) {
            try {
                controller.stopLoop();
            } catch (Exception e) {
                FileLog.e(e);
            }
            controller = null;
        }
    }

    /** Local SOCKS port and a random login, so other apps on the phone cannot use the proxy. */
    private static void ensureSocksAccount(boolean newPort) throws Exception {
        final SharedPreferences prefs = prefs();
        final SharedPreferences.Editor editor = prefs.edit();
        if (newPort || prefs.getInt("socks_port", 0) == 0) {
            try (ServerSocket socket = new ServerSocket(0)) {
                editor.putInt("socks_port", socket.getLocalPort());
            }
        }
        if (prefs.getString("socks_user", "").isEmpty()) {
            editor.putString("socks_user", randomToken()).putString("socks_pass", randomToken());
        }
        editor.commit();
    }

    /** Re-applies Telegram's connection settings; ConnectionsManager picks Merida VPN unless the user set up a proxy. */
    private static void applyToTelegram() {
        AndroidUtilities.runOnUIThread(() -> {
            if (!SharedConfig.isProxyEnabled()) {
                ConnectionsManager.setProxySettings(false, null);
            }
        });
    }

    // ---------- helpers ----------

    private static String randomToken() {
        final byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    private static String sha256Hex(String text) throws Exception {
        final StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream stream = in) {
            final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }
}
