package com.jobbeacon.utils.dedup;

import com.jobbeacon.utils.HtmlTextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class ContentHasher {

    private static final Pattern HTML_TAG = Pattern.compile("<[a-zA-Z/!][^>]*>");

    private ContentHasher() {
    }

    public static String hash(String description) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalize(description).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static String normalize(String description) {
        if (description == null) {
            return "";
        }
        String text = HTML_TAG.matcher(description).find() ? HtmlTextUtils.toPlainText(description) : description;
        return TextNormalizer.fold(text);
    }
}
