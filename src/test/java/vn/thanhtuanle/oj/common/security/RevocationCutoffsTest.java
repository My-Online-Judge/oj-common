package vn.thanhtuanle.oj.common.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared revocation rule. Identity writes both markers; the gateway reads them. Old identity (seconds marker only)
 * and legacy tokens (no {@code iat_ms}) keep the whole-second rule.
 */
class RevocationCutoffsTest {

    /** Revocation at 1000.500 s. */
    private static final Instant REVOKED_AT = Instant.ofEpochMilli(1_000_500);
    private static final String SECONDS = RevocationCutoffs.secondsMarker(REVOKED_AT);
    private static final String MILLIS = RevocationCutoffs.millisMarker(REVOKED_AT);

    @Test
    void markersFromAnInstant() {
        Instant at = Instant.parse("2026-10-21T01:02:03.456Z");

        assertThat(RevocationCutoffs.secondsMarker(at)).isEqualTo(String.valueOf(at.getEpochSecond() + 1));
        assertThat(RevocationCutoffs.millisMarker(at)).isEqualTo(String.valueOf(at.toEpochMilli() + 1));
    }

    @Test
    void theTableUsesTheseMarkers() {
        assertThat(SECONDS).isEqualTo("1001");
        assertThat(MILLIS).isEqualTo("1000501");
    }

    @Test
    void aLoginLaterInTheSameSecondIsKept() {
        assertThat(RevocationCutoffs.revoked(1000, 1_000_600, SECONDS, MILLIS)).isFalse();
    }

    @Test
    void aTokenEarlierInTheSameSecondIsRevoked() {
        assertThat(RevocationCutoffs.revoked(1000, 1_000_400, SECONDS, MILLIS)).isTrue();
    }

    @Test
    void aTokenInTheSameMillisecondIsRevoked() {
        assertThat(RevocationCutoffs.revoked(1000, 1_000_500, SECONDS, MILLIS)).isTrue();
    }

    @Test
    void aLegacyTokenUsesWholeSeconds() {
        assertThat(RevocationCutoffs.revoked(1000, 0, SECONDS, MILLIS)).isTrue();
        assertThat(RevocationCutoffs.revoked(1001, 0, SECONDS, MILLIS)).isFalse();
    }

    /** A later revocation at 2000.100 s, written by one identity as a pair. */
    private static final Instant LATER_AT = Instant.ofEpochMilli(2_000_100);
    private static final String LATER_SECONDS = RevocationCutoffs.secondsMarker(LATER_AT);
    private static final String LATER_MILLIS = RevocationCutoffs.millisMarker(LATER_AT);

    @Test
    void aMillisecondMarkerNewerThanTheSecondsMarkerDecides() {
        // seconds still hold the revocation at 1000.5 s, milliseconds the one at 2000.1 s
        assertThat(RevocationCutoffs.revoked(2000, 2_000_050, SECONDS, LATER_MILLIS)).isTrue();
        assertThat(RevocationCutoffs.revoked(2000, 2_000_200, SECONDS, LATER_MILLIS)).isFalse();
    }

    @Test
    void aSecondsMarkerNewerThanTheMillisecondMarkerDecides() {
        // an identity rolled back to seconds-only wrote the 2000.1 s revocation; the ms key is the stale 1000.5 s one
        assertThat(RevocationCutoffs.revoked(1500, 1_500_000, LATER_SECONDS, MILLIS)).isTrue();
        // a failed ms write after the seconds write behaves the same
        assertThat(RevocationCutoffs.revoked(2000, 2_000_500, LATER_SECONDS, MILLIS)).isTrue();
        assertThat(RevocationCutoffs.revoked(2001, 2_001_000, LATER_SECONDS, MILLIS)).isFalse();
    }

    @Test
    void aMillisecondMarkerAloneDecides() {
        assertThat(RevocationCutoffs.revoked(1000, 1_000_400, null, MILLIS)).isTrue();
        assertThat(RevocationCutoffs.revoked(1000, 1_000_600, null, MILLIS)).isFalse();
    }

    @Test
    void aTokenWithoutIatMsUsesTheSecondsMarkerEvenWhenBothMarkersExist() {
        assertThat(RevocationCutoffs.revoked(2000, 0, LATER_SECONDS, LATER_MILLIS)).isTrue();
        assertThat(RevocationCutoffs.revoked(2001, 0, LATER_SECONDS, LATER_MILLIS)).isFalse();
    }

    @Test
    void aMarkerFromAnOlderIdentityUsesWholeSeconds() {
        assertThat(RevocationCutoffs.revoked(1000, 1_000_900, SECONDS, null)).isTrue();
    }

    @Test
    void noMarkerMeansNotRevoked() {
        assertThat(RevocationCutoffs.revoked(1000, 1_000_400, null, null)).isFalse();
        assertThat(RevocationCutoffs.revoked(1000, 0, null, null)).isFalse();
    }

    @Test
    void anUnparseableMarkerThrows() {
        assertThatThrownBy(() -> RevocationCutoffs.revoked(1000, 1_000_400, SECONDS, "garbage"))
                .isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> RevocationCutoffs.revoked(1000, 0, "garbage", MILLIS))
                .isInstanceOf(NumberFormatException.class);
    }
}
