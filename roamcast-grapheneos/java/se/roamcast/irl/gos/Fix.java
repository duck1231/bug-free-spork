package se.roamcast.irl.gos;

import java.util.Locale;

/**
 * Tolerant URL / stream-key handling so that keys from every platform work,
 * even when they are pasted in "the wrong" field or with stray whitespace.
 */
public final class Fix {

    private static final String[] SCHEMES = {
            "rtmp://", "rtmps://", "rtmpt://", "rtmpts://",
            "rtsp://", "rtsps://", "srt://", "udp://"
    };

    private Fix() {
    }

    /** Removes all whitespace and invisible characters that come along when copying keys. */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)
                    || c == '​' || c == '‌' || c == '‍' || c == '⁠' || c == '﻿') {
                continue;
            }
            b.append(c);
        }
        return b.toString();
    }

    static boolean hasScheme(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        for (String x : SCHEMES) {
            if (l.startsWith(x)) return true;
        }
        return false;
    }

    private static String kindName(Object kind) {
        if (kind instanceof Enum) return ((Enum<?>) kind).name();
        return kind == null ? "" : kind.toString();
    }

    private static String stripTrailingSlashes(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') end--;
        String r = s.substring(0, end);
        return r.endsWith(":") ? s : r; // keep "rtmp://" intact
    }

    /** "host[:port]" part of a URL that has a scheme. */
    private static String authority(String url) {
        int start = url.indexOf("://");
        if (start < 0) return "";
        start += 3;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        return url.substring(start, end);
    }

    /** Path after the authority, without leading slash and without query. */
    private static String path(String url) {
        int start = url.indexOf("://");
        if (start < 0) return "";
        start += 3;
        int slash = url.indexOf('/', start);
        if (slash < 0) return "";
        String p = url.substring(slash + 1);
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        return p;
    }

    private static int segments(String path) {
        int n = 0;
        for (String part : path.split("/")) {
            if (part.length() > 0) n++;
        }
        return n;
    }

    private static boolean hasPort(String authority) {
        String a = authority;
        int at = a.lastIndexOf('@');
        if (at >= 0) a = a.substring(at + 1);
        if (a.startsWith("[")) return a.contains("]:");
        return a.indexOf(':') >= 0;
    }

    /**
     * Builds the final ingest URL from server + key. Replaces Destination.rtmpUrl().
     */
    public static String buildUrl(Object kind, String server, String key) {
        String k = clean(key);
        String s = clean(server);

        // Whole URL (server + key) pasted into the key field.
        if (hasScheme(k) && k.length() > k.indexOf("://") + 3) {
            s = k;
            k = "";
        }

        if (s.length() > 0 && !s.contains("://")) {
            String n = kindName(kind);
            boolean tls = n.equals("KICK") || n.equals("FACEBOOK") || n.equals("INSTAGRAM")
                    || n.equals("X") || n.equals("VIMEO");
            s = (tls ? "rtmps://" : "rtmp://") + s;
        }
        s = stripTrailingSlashes(s);

        String lower = s.toLowerCase(Locale.ROOT);
        // Amazon IVS ingest (Kick, Twitch) always uses the application "app".
        if (lower.contains("contribute.live-video.net") && path(s).isEmpty()) {
            s = s + "/app";
        }
        // RTMPS without port -> 443 (some servers refuse anything else).
        if (lower.startsWith("rtmps://")) {
            String auth = authority(s);
            if (auth.length() > 0 && !hasPort(auth)) {
                int i = s.indexOf("://") + 3 + auth.length();
                s = s.substring(0, i) + ":443" + s.substring(i);
            }
        }

        if (k.startsWith("/")) k = k.substring(1);
        if (k.isEmpty()) return s;
        if (s.endsWith("/" + k)) return s; // key already part of the server URL
        return s + "/" + k;
    }

    /** True when a key is required but neither the key field nor the URL contains one. */
    public static boolean keyMissing(Object kind, String server, String key) {
        String n = kindName(kind);
        if (n.equals("RTMP") || n.equals("SRT") || n.equals("SRTLA")) return false;
        if (clean(key).length() > 0) return false;
        String s = clean(server);
        if (!hasScheme(s)) return true;
        // Server URL already carries the stream name (e.g. TikTok/Rumble full URL).
        return segments(path(s)) < 2 && s.indexOf('?') < 0;
    }

    /** Protocols RootEncoder's GenericStream can send to. */
    public static boolean isSupported(String url) {
        if (url == null) return false;
        String u = url.trim();
        return hasScheme(u) && authority(u).length() > 0;
    }
}
