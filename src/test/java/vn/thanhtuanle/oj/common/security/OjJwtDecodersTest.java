package vn.thanhtuanle.oj.common.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A service keeps verifying the tokens it already holds the key for while the issuer (identity-service) is down, however
 * long the outage, and an unknown key id can make it refetch at most once per interval.
 */
class OjJwtDecodersTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final AtomicReference<String> published = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final MutableClock clock = new MutableClock();
    private HttpServer jwksServer;
    private int port;

    @BeforeEach
    void startJwksServer() throws IOException {
        startServerOn(0);
        port = jwksServer.getAddress().getPort();
    }

    private void startServerOn(int p) throws IOException {
        jwksServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), p), 0);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            requests.incrementAndGet();
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
        return "http://127.0.0.1:" + port + "/.well-known/jwks.json";
    }

    private void publish(RSAKey... keys) {
        published.set(new JWKSet(Arrays.stream(keys).<JWK>map(RSAKey::toPublicJWK).toList()).toString());
    }

    private JwtDecoder decoder() {
        return OjJwtDecoders.fromJwksUri(jwksUri(), new RestTemplate(), clock);
    }

    private static String signedBy(RSAKey key) {
        return TestTokens.sign(key, TestTokens.validClaims().build());
    }

    @Test
    void hundredUnknownKidTokensCauseOneFetch() {
        publish(TestTokens.KEY);
        JwtDecoder decoder = decoder();

        for (int i = 0; i < 100; i++) {
            String token = signedBy(TestTokens.generate("ghost-" + i));
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void aNewKidIsFetchedAfterTheInterval() {
        publish(TestTokens.KEY);
        JwtDecoder decoder = decoder();
        decoder.decode(TestTokens.valid());
        RSAKey b = TestTokens.generate("key-b");
        publish(TestTokens.KEY, b);
        String token = signedBy(b);

        clock.advance(Duration.ofSeconds(29));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        assertThat(requests.get()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(2));
        assertThat(decoder.decode(token).getSubject()).isEqualTo("alice");
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void identityDownKnownKeysStillVerify() {
        publish(TestTokens.KEY);
        JwtDecoder decoder = decoder();
        decoder.decode(TestTokens.valid());

        jwksServer.stop(0); // the issuer goes away
        clock.advance(Duration.ofSeconds(31));
        String unknown = signedBy(TestTokens.generate("ghost"));

        assertThatThrownBy(() -> decoder.decode(unknown)).isInstanceOf(JwtException.class);
        assertThat(decoder.decode(TestTokens.valid()).getSubject()).isEqualTo("alice");
    }

    @Test
    void coldStartRetriesAfterOneSecond() throws IOException {
        publish(TestTokens.KEY);
        jwksServer.stop(0);
        JwtDecoder decoder = decoder();
        assertThatThrownBy(() -> decoder.decode(TestTokens.valid())).isInstanceOf(JwtException.class);

        startServerOn(port);
        clock.advance(Duration.ofMillis(500));
        assertThatThrownBy(() -> decoder.decode(TestTokens.valid())).isInstanceOf(JwtException.class);
        assertThat(requests.get()).as("no request inside the cold-start second").isZero();

        clock.advance(Duration.ofMillis(600));
        assertThat(decoder.decode(TestTokens.valid()).getSubject()).isEqualTo("alice");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void keysNeverExpire() {
        publish(TestTokens.KEY);
        JwtDecoder decoder = decoder();
        decoder.decode(TestTokens.valid());

        clock.advance(Duration.ofDays(1));

        assertThat(decoder.decode(TestTokens.valid()).getSubject()).isEqualTo("alice");
        assertThat(requests.get()).isEqualTo(1);
    }
}
