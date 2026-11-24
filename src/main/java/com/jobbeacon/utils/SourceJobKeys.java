package com.jobbeacon.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What identifies a posting within its source site. It is the path of its url, except on VietnamWorks, which serves one
 * posting under several urls ({@code ...-2132820-jv} and {@code ...-2132820-jd}) that share its numeric id.
 *
 * <p>Titles are deliberately not compared: companies post the same text several times as separate openings.
 * {@code 008_job_source_key.sql} computes the same key for rows stored earlier, so change both together.
 */
public final class SourceJobKeys {

    private static final String VIETNAMWORKS = "vietnamworks.com";
    private static final Pattern VIETNAMWORKS_ID = Pattern.compile("-(\\d+)-j[vd]$");

    private SourceJobKeys() {
    }

    /** Null when the url has no path to tell postings apart by; such a posting is never matched to an earlier one. */
    public static String from(String url) {
        String domain = UrlUtils.extractDomain(url);
        if (domain == null) {
            return null;
        }
        String path = url.replaceFirst("[?#].*$", "")
                .replaceFirst("(?i)^https?://[^/]*", "")
                .replaceFirst("/+$", "");
        if (path.isEmpty()) {
            return null;
        }
        if (VIETNAMWORKS.equals(domain)) {
            Matcher matcher = VIETNAMWORKS_ID.matcher(path);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return path;
    }
}
