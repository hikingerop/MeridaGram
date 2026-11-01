package org.telegram.messenger.merida;

import androidx.exifinterface.media.ExifInterface;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.utils.settings.MeridaSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * MeridaGram: photos sent as files keep their hidden data (EXIF): GPS position, phone model, time,
 * software. Telegram removes it only from compressed photos. With «Remove photo metadata» on, a clean
 * copy is sent instead; the original file in the gallery stays untouched.
 */
public final class MeridaExif {

    private static final String[] TAGS = {
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SERIAL_NUMBER, ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    };

    private MeridaExif() {
    }

    /** Returns the path of a cleaned copy of a photo file, or the original path when there is nothing to do. */
    public static String cleanCopy(String path) {
        if (path == null || !MeridaSettings.stripPhotoMetadata.get()) {
            return path;
        }
        final String lower = path.toLowerCase(Locale.ROOT);
        final String extension;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            extension = ".jpg";
        } else if (lower.endsWith(".png")) {
            extension = ".png";
        } else if (lower.endsWith(".webp")) {
            extension = ".webp";
        } else {
            return path;
        }
        final File source = new File(path);
        if (!source.exists() || source.length() == 0) {
            return path;
        }
        try {
            final String name = source.getName();
            // a folder per send keeps the original file name, so the other person sees the same name
            final File dir = new File(new File(AndroidUtilities.getCacheDir(), "merida_clean"), String.valueOf(System.nanoTime()));
            if (!dir.exists() && !dir.mkdirs()) {
                return path;
            }
            final File copy = new File(dir, name);
            try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(copy)) {
                final byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            final ExifInterface exif = new ExifInterface(copy.getAbsolutePath());
            for (String tag : TAGS) {
                exif.setAttribute(tag, null);
            }
            exif.saveAttributes();
            return copy.getAbsolutePath();
        } catch (Throwable e) {
            FileLog.e(e);
            return path;
        }
    }
}
