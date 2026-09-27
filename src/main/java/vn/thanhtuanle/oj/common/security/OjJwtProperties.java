package vn.thanhtuanle.oj.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwksUri where the issuer publishes its public keys, e.g.
 *                {@code http://identity-service:8000/.well-known/jwks.json}
 */
@ConfigurationProperties("oj.security.jwt")
public record OjJwtProperties(String jwksUri) {
}
