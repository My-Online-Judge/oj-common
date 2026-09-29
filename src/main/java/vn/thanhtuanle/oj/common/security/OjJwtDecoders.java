package vn.thanhtuanle.oj.common.security;

import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Objects;

/** How every OJ service decodes the access tokens identity issues. */
public final class OjJwtDecoders {

    /** Claim carrying the user's UUID; a token without it is not an OJ access token. */
    public static final String UID_CLAIM = "uid";

    private OjJwtDecoders() {
    }

    /**
     * RS256 only, keys fetched from the issuer's JWKS, timestamps checked (60s skew), uid required.
     *
     * <p>The key set lives in a cache without a time-to-live, so tokens signed by a known key keep
     * verifying through an issuer outage of any length, while a token with an unknown key id still
     * triggers a refetch (key rotation). Spring's default keeps the keys for Nimbus's 5 minutes and
     * then fails every token until the issuer is back.
     */
    public static JwtDecoder fromJwksUri(String jwksUri) {
        return fromJwksUri(jwksUri, new ConcurrentMapCache("oj-jwks"));
    }

    static JwtDecoder fromJwksUri(String jwksUri, Cache keySetCache) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .cache(keySetCache)
                .build();
        decoder.setJwtValidator(validator());
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validator() {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<Object>(UID_CLAIM, Objects::nonNull));
    }
}
