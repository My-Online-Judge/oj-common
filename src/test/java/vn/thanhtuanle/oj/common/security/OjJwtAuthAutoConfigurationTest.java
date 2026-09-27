package vn.thanhtuanle.oj.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OjJwtAuthAutoConfigurationTest {

    private static HttpServer jwksServer;

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OjJwtAuthAutoConfiguration.class));

    /** A real JWKS endpoint, like the issuer's /.well-known/jwks.json. */
    @BeforeAll
    static void startJwksServer() throws IOException {
        byte[] body = new JWKSet(TestTokens.KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        jwksServer.start();
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    private static String jwksUri() {
        return "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
    }

    @Test
    void nothingIsWiredWithoutAJwksUri() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(JwtDecoder.class);
            assertThat(context).doesNotHaveBean(OjJwtAuthenticationFilter.class);
        });
    }

    @Test
    void theFilterIsOnlyWiredIntoTheSecurityChainNotTheServletContainer() {
        runner.withPropertyValues("oj.security.jwt.jwks-uri=" + jwksUri()).run(context -> {
            assertThat(context).hasSingleBean(OjJwtAuthenticationFilter.class);
            assertThat(context).hasSingleBean(CurrentUser.class);
            assertThat(context.getBean(FilterRegistrationBean.class).isEnabled()).isFalse();
        });
    }

    @Test
    void tokensAreVerifiedAgainstTheLiveJwksEndpoint() {
        runner.withPropertyValues("oj.security.jwt.jwks-uri=" + jwksUri()).run(context ->
                assertThat(context.getBean(JwtDecoder.class).decode(TestTokens.valid()).getSubject())
                        .isEqualTo("alice"));
    }
}
