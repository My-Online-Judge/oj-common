package vn.thanhtuanle.oj.common.tls;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.cert.X509Certificate;
import java.util.Optional;

/** Reads the identity of a TLS peer from its certificate. */
public final class PeerCertificates {

    private PeerCertificates() {
    }

    /** The {@code CN} of the certificate subject, empty when the subject has none. */
    public static Optional<String> commonName(X509Certificate leaf) {
        try {
            for (Rdn rdn : new LdapName(leaf.getSubjectX500Principal().getName()).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return Optional.of(rdn.getValue().toString());
                }
            }
            return Optional.empty();
        } catch (InvalidNameException e) {
            return Optional.empty();
        }
    }
}
