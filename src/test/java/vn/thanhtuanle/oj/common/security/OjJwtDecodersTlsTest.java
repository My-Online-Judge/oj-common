package vn.thanhtuanle.oj.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The JWKS endpoint demands a client certificate; the decoder must present the service's own. */
class OjJwtDecodersTlsTest {

    private static final String TLS = "classpath:tls/";
    private static HttpsServer server;

    @BeforeAll
    static void startServer() throws Exception {
        SslBundle serverBundle = SslBundle.of(new PemSslStoreBundle(
                new PemSslStoreDetails(null, TLS + "server.crt", TLS + "server.key"),
                PemSslStoreDetails.forCertificate(TLS + "ca.crt")));
        SSLContext context = serverBundle.createSslContext();
        byte[] body = new JWKSet(TestTokens.KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters parameters = context.getDefaultSSLParameters();
                parameters.setNeedClientAuth(true);
                params.setSSLParameters(parameters);
            }
        });
        server.createContext("/.well-known/jwks.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static String jwksUri() {
        return "https://localhost:" + server.getAddress().getPort() + "/.well-known/jwks.json";
    }

    @Test
    void decodesWithBundleClient() {
        SslBundle client = SslBundle.of(new PemSslStoreBundle(
                new PemSslStoreDetails(null, TLS + "api-gateway.crt", TLS + "api-gateway.key"),
                PemSslStoreDetails.forCertificate(TLS + "ca.crt")));
        RestTemplate rest = new RestTemplateBuilder().sslBundle(client).build();

        assertThat(OjJwtDecoders.fromJwksUri(jwksUri(), rest).decode(TestTokens.valid()).getSubject())
                .isEqualTo("alice");
    }

    @Test
    void failsWithoutClientCert() {
        // Trusts the CA (so the server is accepted) but presents no client certificate. TLS 1.3 reports the
        // rejection after the handshake, as an I/O error on the first read, so the cause is an IOException.
        SslBundle trustOnly = SslBundle.of(new PemSslStoreBundle(
                null, PemSslStoreDetails.forCertificate(TLS + "ca.crt")));
        JwtDecoder decoder = OjJwtDecoders.fromJwksUri(
                jwksUri(), new RestTemplateBuilder().sslBundle(trustOnly).build());

        assertThatThrownBy(() -> decoder.decode(TestTokens.valid()))
                .isInstanceOf(JwtException.class)
                .hasRootCauseInstanceOf(IOException.class);
    }

    @Test
    void autoConfigUsesBundle() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class, OjJwtAuthAutoConfiguration.class))
                .withPropertyValues(
                        "spring.ssl.bundle.pem.oj.keystore.certificate=" + TLS + "api-gateway.crt",
                        "spring.ssl.bundle.pem.oj.keystore.private-key=" + TLS + "api-gateway.key",
                        "spring.ssl.bundle.pem.oj.truststore.certificate=" + TLS + "ca.crt",
                        "oj.security.jwt.jwks-uri=" + jwksUri(),
                        "oj.security.jwt.jwks-ssl-bundle=oj")
                .run(context -> assertThat(context.getBean(JwtDecoder.class).decode(TestTokens.valid()).getSubject())
                        .isEqualTo("alice"));
    }
}
