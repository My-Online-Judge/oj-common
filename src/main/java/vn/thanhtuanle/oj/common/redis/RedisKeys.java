package vn.thanhtuanle.oj.common.redis;

import java.util.Locale;
import java.util.UUID;

/** Redis keys that one service writes and another reads. */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** Present while an IP or device ban is active: {@code oj:ban:<ip|device>:<value>}. */
    public static String accessBan(String type, String value) {
        return "oj:ban:" + type.toLowerCase(Locale.ROOT) + ":" + value;
    }

    /** Present while one revoked access token (logout) would still be valid: {@code oj:token:blocklist:<jti>}. */
    public static String tokenBlocklist(String jti) {
        return "oj:token:blocklist:" + jti;
    }

    /**
     * Epoch second before which every access token of this user is revoked (role change, disable,
     * delete): {@code oj:token:revoked-before:<userId>}.
     */
    public static String tokenRevokedBefore(UUID userId) {
        return "oj:token:revoked-before:" + userId;
    }
}
