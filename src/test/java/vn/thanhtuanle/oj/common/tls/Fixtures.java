package vn.thanhtuanle.oj.common.tls;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

final class Fixtures {

    private Fixtures() {
    }

    static X509Certificate cert(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/tls/" + name + ".crt")) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
