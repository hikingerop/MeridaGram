package org.telegram.messenger.merida;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MeridaGram: crash report. When the app crashes, the reason (stack trace) is saved to a file inside the app;
 * on the next start a window shows it with «Copy» / «Share», so the owner can send it to fix the crash.
 * Nothing is sent anywhere by itself.
 */
public class MeridaCrash {

    private static final String FILE = "merida_crash.txt";
    private static Context context;

    public static void install(Context ctx) {
        if (ctx == null || context != null) return;
        context = ctx;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                save(thread, error);
            } catch (Throwable ignore) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private static void save(Thread thread, Throwable error) throws Exception {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        pw.println("MeridaGram build " + MeridaBuild.NUMBER);
        pw.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Thread: " + (thread != null ? thread.getName() : "?"));
        pw.println();
        error.printStackTrace(pw);
        pw.flush();
        String text = sw.toString();
        if (text.length() > 30000) {
            text = text.substring(0, 30000);
        }
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), FILE))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    /** On start: if the app crashed last time, shows the report once. */
    public static void showIfAny(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final File f = new File(activity.getFilesDir(), FILE);
        if (!f.exists()) return;
        String report;
        try {
            final byte[] data = new byte[(int) Math.min(f.length(), 64000)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int read = 0;
                while (read < data.length) {
                    final int n = in.read(data, read, data.length - read);
                    if (n < 0) break;
                    read += n;
                }
            }
            report = new String(data, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            report = null;
        }
        f.delete();
        if (report == null || report.isEmpty()) return;
        final String text = report;
        try {
            final AlertDialog.Builder b = new AlertDialog.Builder(activity);
            b.setTitle("MeridaGram вылетел");
            b.setMessage("В прошлый раз приложение закрылось с ошибкой. Скопируй отчёт и пришли его в чат, чтобы это исправить.\n\n"
                    + (text.length() > 1500 ? text.substring(0, 1500) + "…" : text));
            b.setPositiveButton("Скопировать", (d, w) -> {
                try {
                    final ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("MeridaGram crash", text));
                    Toast.makeText(activity, "Отчёт скопирован", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
            });
            b.setNeutralButton("Поделиться", (d, w) -> {
                try {
                    final Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    activity.startActivity(Intent.createChooser(i, "Отчёт о вылете"));
                } catch (Throwable ignore) {
                }
            });
            b.setNegativeButton("Закрыть", null);
            b.show();
        } catch (Throwable ignore) {
        }
    }
}
