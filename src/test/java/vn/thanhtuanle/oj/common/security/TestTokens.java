package vn.thanhtuanle.oj.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Test-only RSA keys and RS256 tokens shaped like the ones judge-api / identity-service issue. */
final class TestTokens {

    static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    static final RSAKey KEY = generate("test-kid");

    private TestTokens() {
    }

    static RSAKey generate(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject("alice")
                .jwtID(UUID.randomUUID().toString())
                .claim(OjJwtDecoders.UID_CLAIM, USER_ID.toString())
                .claim(OjJwtAuthenticationFilter.AUTHORITIES_CLAIM, List.of("ADMIN", "problem:create"))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)));
    }

    static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    static String valid() {
        return sign(KEY, validClaims().build());
    }

    /** Same validation rules as production, but keyed directly instead of through JWKS. */
    static JwtDecoder decoderFor(RSAKey key) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(OjJwtDecoders.validator());
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
