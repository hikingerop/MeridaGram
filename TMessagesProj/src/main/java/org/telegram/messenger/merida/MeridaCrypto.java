package org.telegram.messenger.merida;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * MeridaGram encryption over Telegram. Two people agree on a password in person and enter it in the
 * chat (⋮ → MeridaGram encryption). Text messages are then sent as AES-256-GCM ciphertext, so Telegram's
 * servers and anyone without the password see only «🔐MG1:…». MeridaGram on the other side, with the
 * same password, shows the real text. The password itself never leaves the phone; only a key derived
 * from it is stored, per chat.
 */
public final class MeridaCrypto {

    public static final String MARKER = "🔐MG1:";

    /** Telegram's limit is 4096 characters per message; leave room for the marker. */
    private static final int MAX_MESSAGE_CHARS = 4000;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 20000;
    /** Same on both phones, so both derive the same key from the same password. */
    private static final byte[] SALT = "MeridaGram-E2E-v1".getBytes(StandardCharsets.UTF_8);

    private static final HashMap<String, byte[]> keyCache = new HashMap<>();
    private static final SecureRandom random = new SecureRandom();

    private MeridaCrypto() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("merida_crypto", Context.MODE_PRIVATE);
    }

    private static String id(int account, long dialogId) {
        return account + "_" + dialogId;
    }

    // ---- settings per chat ------------------------------------------------------------------

    /** True when this chat has a password and sending encrypted messages is on. */
    public static boolean isOn(int account, long dialogId) {
        return prefs().getBoolean("on_" + id(account, dialogId), false) && key(account, dialogId) != null;
    }

    public static boolean hasPassword(int account, long dialogId) {
        return key(account, dialogId) != null;
    }

    /** Derives the key from the password, saves it for this chat and turns encryption on. */
    public static void setPassword(int account, long dialogId, String password) {
        final byte[] key = pbkdf2(password.getBytes(StandardCharsets.UTF_8), SALT, PBKDF2_ITERATIONS);
        synchronized (keyCache) {
            keyCache.put(id(account, dialogId), key);
        }
        prefs().edit()
                .putString("key_" + id(account, dialogId), Base64.encodeToString(key, Base64.NO_WRAP))
                .putBoolean("on_" + id(account, dialogId), true)
                .apply();
    }

    /** Stops encrypting new messages; old ones still open because the key is kept. */
    public static void setOn(int account, long dialogId, boolean on) {
        prefs().edit().putBoolean("on_" + id(account, dialogId), on).apply();
    }

    /** Forgets the password: encrypted messages in this chat can no longer be read. */
    public static void forget(int account, long dialogId) {
        synchronized (keyCache) {
            keyCache.remove(id(account, dialogId));
        }
        prefs().edit().remove("key_" + id(account, dialogId)).remove("on_" + id(account, dialogId)).apply();
    }

    private static byte[] key(int account, long dialogId) {
        final String id = id(account, dialogId);
        synchronized (keyCache) {
            if (keyCache.containsKey(id)) {
                return keyCache.get(id);
            }
        }
        byte[] key = null;
        final String stored = prefs().getString("key_" + id, null);
        if (stored != null) {
            try {
                key = Base64.decode(stored, Base64.NO_WRAP);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        synchronized (keyCache) {
            keyCache.put(id, key);
        }
        return key;
    }

    // ---- messages ---------------------------------------------------------------------------

    public static boolean isEncrypted(CharSequence text) {
        return text != null && text.toString().startsWith(MARKER);
    }

    /**
     * Encrypts a text message. Long texts become several messages, each within Telegram's limit.
     * Returns null when encryption fails: the caller must then not send anything, never the plain text.
     */
    public static List<String> encryptParts(int account, long dialogId, String text) {
        final byte[] key = key(account, dialogId);
        if (key == null || text == null) {
            return null;
        }
        final List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = text.length();
            String encrypted = encrypt(key, text.substring(start, end));
            // shrink the piece until its ciphertext fits into one message
            while (encrypted != null && encrypted.length() > MAX_MESSAGE_CHARS && end - start > 1) {
                end = start + Math.max(1, (end - start) * 3 / 4);
                if (Character.isHighSurrogate(text.charAt(end - 1)) && end < text.length()) {
                    end++;
                }
                encrypted = encrypt(key, text.substring(start, end));
            }
            if (encrypted == null) {
                return null;
            }
            parts.add(encrypted);
            start = end;
        }
        return parts.isEmpty() ? null : parts;
    }

    /**
     * What to show instead of an encrypted message: the real text with a lock, or a note that the
     * password is missing or wrong. Null when the text is not a MeridaGram encrypted message.
     */
    public static CharSequence decryptForDisplay(int account, long dialogId, String text) {
        if (!isEncrypted(text)) {
            return null;
        }
        final byte[] key = key(account, dialogId);
        if (key == null) {
            return "🔐 " + LocaleController.getString(R.string.MeridaCryptoLocked);
        }
        final String plain = decrypt(key, text);
        if (plain == null) {
            return "🔐 " + LocaleController.getString(R.string.MeridaCryptoWrongPassword);
        }
        return "🔒 " + plain;
    }

    private static String encrypt(byte[] key, String plain) {
        try {
            final byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            final byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            final byte[] all = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(body, 0, all, iv.length, body.length);
            return MARKER + Base64.encodeToString(all, Base64.NO_WRAP);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String decrypt(byte[] key, String text) {
        try {
            final byte[] all = Base64.decode(text.substring(MARKER.length()).trim(), Base64.NO_WRAP);
            if (all.length <= IV_BYTES) {
                return null;
            }
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            final byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** PBKDF2-HMAC-SHA256 for one 32-byte block (works on every Android version). */
    private static byte[] pbkdf2(byte[] password, byte[] salt, int iterations) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password.length == 0 ? new byte[1] : password, "HmacSHA256"));
            mac.update(salt);
            mac.update(new byte[]{0, 0, 0, 1});
            byte[] u = mac.doFinal();
            final byte[] result = u.clone();
            for (int i = 1; i < iterations; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < result.length; j++) {
                    result[j] ^= u[j];
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
