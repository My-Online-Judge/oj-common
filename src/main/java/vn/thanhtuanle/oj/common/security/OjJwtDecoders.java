package vn.thanhtuanle.oj.common.security;

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

    /** RS256 only, keys fetched from the issuer's JWKS, timestamps checked (60s skew), uid required. */
    public static JwtDecoder fromJwksUri(String jwksUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
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
