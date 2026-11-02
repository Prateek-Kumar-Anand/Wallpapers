package com.example.motiongifwallpaper;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Validated, size-limited access to the user-selected GIF. */
final class GifSource {
    static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 80;

    private GifSource() {}

    /** Only content:// URIs from the system picker are accepted (never file://, http://, etc.). */
    static boolean isAllowedUri(Uri uri) {
        return uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme());
    }

    private static boolean isGifHeader(byte[] b, int len) {
        return len >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
    }

    /** Reads only the first bytes to confirm the file really is a GIF. */
    static boolean hasGifHeader(ContentResolver resolver, Uri uri) {
        if (!isAllowedUri(uri)) return false;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return false;
            byte[] head = new byte[6];
            int read = 0;
            while (read < head.length) {
                int n = in.read(head, read, head.length - read);
                if (n < 0) break;
                read += n;
            }
            return isGifHeader(head, read);
        } catch (Exception e) {
            return false;
        }
    }

    /** Reads the whole GIF, refusing anything that is not a GIF or exceeds {@link #MAX_BYTES}. */
    static byte[] read(ContentResolver resolver, Uri uri) throws IOException {
        if (!isAllowedUri(uri)) throw new IOException("Unsupported URI");
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open GIF");
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) throw new IOException("GIF too large");
                out.write(buf, 0, n);
            }
            byte[] bytes = out.toByteArray();
            if (!isGifHeader(bytes, bytes.length)) throw new IOException("Not a GIF");
            return bytes;
        }
    }

    /** File size reported by the provider, or -1 if unknown. */
    static long size(ContentResolver resolver, Uri uri) {
        try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {
        }
        return -1;
    }

    /** Provider-supplied name, stripped of control characters and truncated for safe display. */
    static String displayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) name = c.getString(0);
        } catch (Exception ignored) {
        }
        if (name == null) return "GIF file";
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) return "GIF file";
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) + "…" : name;
    }
}
