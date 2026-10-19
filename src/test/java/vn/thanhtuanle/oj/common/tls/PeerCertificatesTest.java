package vn.thanhtuanle.oj.common.tls;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PeerCertificatesTest {

    @Test
    void cnOfFixtureLeaf() {
        assertThat(PeerCertificates.commonName(Fixtures.cert("api-gateway"))).contains("api-gateway");
    }

    @Test
    void subjectWithoutCnIsEmpty() {
        assertThat(PeerCertificates.commonName(Fixtures.cert("no-cn"))).isEqualTo(Optional.empty());
    }
}
