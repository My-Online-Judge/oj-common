package vn.thanhtuanle.oj.common.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden values computed independently (Python hashlib) from the rule judge-api's ClientMeta has
 * always applied. identity-service writes device bans with this hash and the gateway reads them
 * with it; a one-character drift would make every device ban silently miss.
 */
class ClientFingerprintTest {

    private static final String UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36";

    @Test
    void deviceIdHeaderWinsAndIsTrimmed() {
        assertThat(ClientFingerprint.deviceHash("  device-42  ", UA)).isEqualTo("device-42");
    }

    @Test
    void deviceIdIsCappedAt128Chars() {
        assertThat(ClientFingerprint.deviceHash("x".repeat(200), UA)).isEqualTo("x".repeat(128));
    }

    @Test
    void withoutDeviceIdTheUserAgentIsHashed() {
        assertThat(ClientFingerprint.deviceHash(null, UA))
                .isEqualTo("eb49c5f713f180d669fb9a41bbd633347d2bb336f1a9960c3b7317bc944c4e32");
        assertThat(ClientFingerprint.deviceHash("   ", UA))
                .isEqualTo("eb49c5f713f180d669fb9a41bbd633347d2bb336f1a9960c3b7317bc944c4e32");
    }

    @Test
    void onlyTheFirst512UserAgentCharsAreHashed() {
        assertThat(ClientFingerprint.deviceHash(null, "a".repeat(600)))
                .isEqualTo("471be6558b665e4f6dd49f1184814d1491b0315d466beea768c153cc5500c836");
    }

    @Test
    void noDeviceIdAndNoUserAgentMeansNoDevice() {
        assertThat(ClientFingerprint.deviceHash(null, null)).isNull();
    }
}
