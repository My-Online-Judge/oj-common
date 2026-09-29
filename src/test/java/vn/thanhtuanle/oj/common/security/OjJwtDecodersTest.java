package vn.thanhtuanle.oj.common.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A service must keep verifying the tokens it already holds the key for while the issuer
 * (identity-service) is down — however long the outage. Spring's default only caches the key set
 * for Nimbus's 5 minutes and then fails every token.
 */
class OjJwtDecodersTest {

    private final AtomicReference<String> published = new AtomicReference<>();
    private HttpServer jwksServer;

    @BeforeEach
    void startJwksServer() throws IOException {
        jwksServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            byte[] body = published.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        jwksServer.start();
    }

    @AfterEach
    void stopJwksServer() {
        jwksServer.stop(0);
    }

    private String jwksUri() {
        return "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
    }

    private void publish(RSAKey... keys) {
        published.set(new JWKSet(java.util.Arrays.stream(keys).<JWK>map(RSAKey::toPublicJWK).toList()).toString());
    }

    @Test
    void onceFetchedTheKeysAreServedFromACacheThatNeverExpires() {
        publish(TestTokens.KEY);
        ConcurrentMapCache cache = new ConcurrentMapCache("test-jwks");
        JwtDecoder decoder = OjJwtDecoders.fromJwksUri(jwksUri(), cache);

        decoder.decode(TestTokens.valid());
        assertThat(cache.getNativeCache()).as("the key set is held by a cache without a time-to-live").isNotEmpty();

        jwksServer.stop(0); // the issuer goes away
        assertThat(decoder.decode(TestTokens.valid()).getSubject()).isEqualTo("alice");
    }

    @Test
    void aTokenSignedByANewKeyStillTriggersARefetch() {
        publish(TestTokens.KEY);
        JwtDecoder decoder = OjJwtDecoders.fromJwksUri(jwksUri(), new ConcurrentMapCache("test-jwks"));
        decoder.decode(TestTokens.valid());

        RSAKey rotated = TestTokens.generate("rotated-kid");
        publish(TestTokens.KEY, rotated);
        String signedByTheNewKey = TestTokens.sign(rotated, TestTokens.validClaims().build());

        assertThat(decoder.decode(signedByTheNewKey).getSubject()).isEqualTo("alice");
    }
}
