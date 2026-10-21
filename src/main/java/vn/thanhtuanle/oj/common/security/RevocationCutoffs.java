package vn.thanhtuanle.oj.common.security;

import java.time.Instant;

/**
 * The per-user revocation rule that identity writes and the gateway checks.
 *
 * <p>Identity stores two markers when it revokes a user's sessions: the epoch second after the revocation
 * ({@link vn.thanhtuanle.oj.common.redis.RedisKeys#tokenRevokedBefore}) and the epoch millisecond after it
 * ({@link vn.thanhtuanle.oj.common.redis.RedisKeys#tokenRevokedBeforeMillis}). A token that carries
 * {@link OjJwtDecoders#IAT_MS_CLAIM} is judged by the millisecond marker when one exists, so a login later in the
 * same second survives. A legacy token, or a marker written by an identity that only knows seconds, falls back to the
 * whole-second rule.
 */
public final class RevocationCutoffs {

    private RevocationCutoffs() {
    }

    /** The seconds marker: every token issued before the next whole second is revoked. */
    public static String secondsMarker(Instant revokedAt) {
        return String.valueOf(revokedAt.getEpochSecond() + 1);
    }

    /** The millisecond marker: every token issued at or before {@code revokedAt}'s millisecond is revoked. */
    public static String millisMarker(Instant revokedAt) {
        return String.valueOf(revokedAt.toEpochMilli() + 1);
    }

    /**
     * Whether a token issued at {@code iatSeconds} / {@code iatMillis} is revoked by these markers.
     *
     * @param iatMillis the token's {@code iat_ms}, or 0 when it has none
     * @param secondsCutoff the stored seconds marker, or null
     * @param millisCutoff the stored millisecond marker, or null
     * @throws NumberFormatException when the marker used is not a number
     */
    public static boolean revoked(long iatSeconds, long iatMillis, String secondsCutoff, String millisCutoff) {
        if (iatMillis > 0 && millisCutoff != null) {
            return iatMillis < Long.parseLong(millisCutoff);
        }
        if (secondsCutoff != null) {
            return iatSeconds < Long.parseLong(secondsCutoff);
        }
        return false;
    }
}
