package vn.thanhtuanle.oj.common.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The device a request comes from, as judge-api has always computed it: the client-supplied
 * {@code X-Device-Id} (trimmed, at most 128 chars) when present, otherwise the SHA-256 hex of the
 * {@code User-Agent} (first 512 chars). Whoever writes a device ban and whoever checks it must
 * agree on this byte for byte, or device bans silently never match.
 */
public final class ClientFingerprint {

    public static final int USER_AGENT_MAX = 512;
    public static final int DEVICE_MAX = 128;

    private ClientFingerprint() {
    }

    public static String deviceHash(String deviceIdHeader, String userAgent) {
        if (deviceIdHeader != null && !deviceIdHeader.isBlank()) {
            return truncate(deviceIdHeader.trim(), DEVICE_MAX);
        }
        String ua = truncate(userAgent, USER_AGENT_MAX);
        return ua != null ? truncate(sha256Hex(ua), DEVICE_MAX) : null;
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
