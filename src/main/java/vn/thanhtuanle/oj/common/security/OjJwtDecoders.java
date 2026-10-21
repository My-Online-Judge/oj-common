package vn.thanhtuanle.oj.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.web.client.RestOperations;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** How every OJ service decodes the access tokens identity issues. */
public final class OjJwtDecoders {

    /** Claim carrying the user's UUID; a token without it is not an OJ access token. */
    public static final String UID_CLAIM = "uid";

    /** The access token's issue time in epoch milliseconds, the same instant as {@code iat}. */
    public static final String IAT_MS_CLAIM = "iat_ms";

    private OjJwtDecoders() {
    }

    /** At most one refetch per this interval, triggered only by a key id the held set does not contain. */
    public static final Duration JWKS_REFETCH_INTERVAL = Duration.ofSeconds(30);

    /** Until a key set has loaded once, a retry is allowed after this long. */
    public static final Duration JWKS_COLD_START_RETRY = Duration.ofSeconds(1);

    /** Connect and read timeout of a JWKS fetch, which holds the key source's lock. */
    public static final Duration JWKS_FETCH_TIMEOUT = Duration.ofSeconds(3);

    /** A builder whose clients give up on a hung issuer after {@link #JWKS_FETCH_TIMEOUT}. */
    public static RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder().connectTimeout(JWKS_FETCH_TIMEOUT).readTimeout(JWKS_FETCH_TIMEOUT);
    }

    /**
     * RS256 only, keys fetched from the issuer's JWKS, timestamps checked (60s skew), uid required.
     *
     * <p>The key set never expires, so tokens signed by a known key keep verifying through an issuer outage of any
     * length, while a token with an unknown key id triggers a refetch (key rotation) at most every
     * {@link #JWKS_REFETCH_INTERVAL}. A failed refetch keeps the last good set. Spring's default keeps the keys for
     * Nimbus's 5 minutes and then fails every token until the issuer is back, and refetches for every unknown kid.
     */
    public static JwtDecoder fromJwksUri(String jwksUri) {
        return fromJwksUri(jwksUri, restTemplateBuilder().build());
    }

    /** Same as {@link #fromJwksUri(String)}, fetching the key set through {@code rest} (e.g. one presenting a client certificate). */
    public static JwtDecoder fromJwksUri(String jwksUri, RestOperations rest) {
        return fromJwksUri(jwksUri, rest, Clock.systemUTC());
    }

    static JwtDecoder fromJwksUri(String jwksUri, RestOperations rest, Clock clock) {
        JWKSource<SecurityContext> source = new ThrottledJwkSetSource(
                () -> fetch(jwksUri, rest), JWKS_REFETCH_INTERVAL, JWKS_COLD_START_RETRY, clock);
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source));
        // Spring's validator checks the claims.
        processor.setJWTClaimsSetVerifier((claims, context) -> { });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(validator());
        return decoder;
    }

    private static JWKSet fetch(String jwksUri, RestOperations rest) {
        try {
            return JWKSet.parse(rest.getForObject(jwksUri, String.class));
        } catch (ParseException e) {
            throw new IllegalStateException("Malformed JWKS: " + e.getMessage(), e);
        }
    }

    public static OAuth2TokenValidator<Jwt> validator() {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<Object>(UID_CLAIM, Objects::nonNull));
    }
}
