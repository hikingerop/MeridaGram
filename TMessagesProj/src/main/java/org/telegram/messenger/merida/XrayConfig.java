package org.telegram.messenger.merida;

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Turns a vless:// key from the Merida VPN server (Reality or the XHTTP fallback) into an Xray config
 * whose only inbound is a password-protected SOCKS proxy on 127.0.0.1 for Telegram.
 */
final class XrayConfig {

    private XrayConfig() {
    }

    static String build(String key, int socksPort, String socksUser, String socksPassword) throws JSONException {
        final Uri uri = Uri.parse(key.trim());
        if (!"vless".equalsIgnoreCase(uri.getScheme()) || TextUtils.isEmpty(uri.getUserInfo()) || TextUtils.isEmpty(uri.getHost()) || uri.getPort() <= 0) {
            throw new IllegalArgumentException("Not a vless key");
        }
        String host = uri.getHost();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        final JSONObject user = new JSONObject()
                .put("address", host)
                .put("port", uri.getPort())
                .put("id", uri.getUserInfo())
                .put("encryption", param(uri, "encryption", "none"));
        final String flow = param(uri, "flow", "");
        if (!flow.isEmpty()) {
            user.put("flow", flow);
        }

        final String network = param(uri, "type", "tcp");
        final String security = param(uri, "security", "none");
        final JSONObject stream = new JSONObject().put("network", network).put("security", security);
        if ("reality".equals(security)) {
            final JSONObject reality = new JSONObject()
                    .put("serverName", param(uri, "sni", ""))
                    .put("fingerprint", param(uri, "fp", "chrome"))
                    .put("publicKey", param(uri, "pbk", ""))
                    .put("shortId", param(uri, "sid", ""))
                    .put("spiderX", param(uri, "spx", "/"));
            putIfSet(reality, "mldsa65Verify", param(uri, "pqv", ""));
            stream.put("realitySettings", reality);
        } else if ("tls".equals(security)) {
            final JSONObject tls = new JSONObject().put("fingerprint", param(uri, "fp", "chrome"));
            putIfSet(tls, "serverName", param(uri, "sni", ""));
            putIfSet(tls, "pinnedPeerCertSha256", param(uri, "pcs", ""));
            final String alpn = param(uri, "alpn", "");
            if (!alpn.isEmpty()) {
                final JSONArray list = new JSONArray();
                for (String value : alpn.split(",")) {
                    if (!value.trim().isEmpty()) {
                        list.put(value.trim());
                    }
                }
                tls.put("alpn", list);
            }
            stream.put("tlsSettings", tls);
        }
        if ("xhttp".equals(network)) {
            final JSONObject xhttp = new JSONObject()
                    .put("path", param(uri, "path", "/"))
                    .put("mode", param(uri, "mode", "auto"));
            putIfSet(xhttp, "host", param(uri, "host", ""));
            final String extra = param(uri, "extra", "");
            if (!extra.isEmpty()) {
                xhttp.put("extra", new JSONObject(extra));
            }
            stream.put("xhttpSettings", xhttp);
        }

        final JSONObject socks = new JSONObject()
                .put("tag", "telegram")
                .put("listen", "127.0.0.1")
                .put("port", socksPort)
                .put("protocol", "socks")
                .put("settings", new JSONObject()
                        .put("auth", "password")
                        .put("udp", false)
                        .put("accounts", new JSONArray().put(new JSONObject().put("user", socksUser).put("pass", socksPassword))));
        final JSONObject proxy = new JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vless")
                .put("settings", user)
                .put("streamSettings", stream);

        return new JSONObject()
                .put("log", new JSONObject().put("loglevel", "warning"))
                .put("inbounds", new JSONArray().put(socks))
                .put("outbounds", new JSONArray().put(proxy))
                .toString();
    }

    private static String param(Uri uri, String name, String defaultValue) {
        final String value = uri.getQueryParameter(name);
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    private static void putIfSet(JSONObject object, String name, String value) throws JSONException {
        if (!value.isEmpty()) {
            object.put(name, value);
        }
    }
}
