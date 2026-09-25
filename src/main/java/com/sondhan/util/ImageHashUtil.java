package com.sondhan.util;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** SHA-256 hashing utility used by PreloadedDatabase to identify uploaded images. */
public class ImageHashUtil {
    public static String hashFile(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = fis.read(buf)) != -1) md.update(buf, 0, n);
        }
        return toHex(md.digest());
    }
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}