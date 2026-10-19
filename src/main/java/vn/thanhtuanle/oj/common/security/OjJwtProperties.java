package vn.thanhtuanle.oj.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwksUri where the issuer publishes its public keys, e.g.
 *                {@code https://identity-service:8000/.well-known/jwks.json}
 * @param jwksSslBundle optional Spring SSL bundle name; when set, the JWKS is fetched with that bundle's
 *                      client certificate and trust (mTLS)
 */
@ConfigurationProperties("oj.security.jwt")
public record OjJwtProperties(String jwksUri, String jwksSslBundle) {
}
