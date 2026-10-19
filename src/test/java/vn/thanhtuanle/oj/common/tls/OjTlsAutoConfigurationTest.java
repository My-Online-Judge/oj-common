package vn.thanhtuanle.oj.common.tls;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import jakarta.servlet.FilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OjTlsAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OjTlsAutoConfiguration.class));

    @Test
    void noFilterWhenListEmpty() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(PeerCertificateFilter.class));
    }

    @Test
    void filterWhenListSet() {
        runner.withPropertyValues("oj.tls.allowed-clients=edge,api-gateway")
                .run(ctx -> assertThat(ctx).hasSingleBean(PeerCertificateFilter.class));
    }

    @Test
    void filterWhenListSetAsIndexedProperty() {
        runner.withPropertyValues("oj.tls.allowed-clients[0]=edge")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PeerCertificateFilter.class);
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    ctx.getBean(PeerCertificateFilter.class)
                            .doFilter(new MockHttpServletRequest(), response, mock(FilterChain.class));
                    assertThat(response.getStatus()).isEqualTo(403);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"need", "want", "NEED"})
    void clientCertificatesWithoutAnAllowlistFailTheStartup(String clientAuth) {
        // Every certificate the CA signed (tls-probe, a worker's) would otherwise reach the API.
        runner.withPropertyValues("server.ssl.client-auth=" + clientAuth)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("oj.tls.allowed-clients");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"oj.tls.allowed-clients=", "oj.tls.allowed-clients= ", "oj.tls.allowed-clients= , ",
            "oj.tls.allowed-clients[0]=", "oj.tls.allowed-clients[0]= "})
    void aBlankOnlyAllowlistIsNoAllowlist(String property) {
        // Set untrimmed (withPropertyValues trims): a YAML entry "- ' '" binds to exactly this.
        int eq = property.indexOf('=');
        runner.withPropertyValues("server.ssl.client-auth=need")
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "blank", Map.of(property.substring(0, eq), property.substring(eq + 1)))))
                .run(ctx -> assertThat(ctx.getStartupFailure()).rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("oj.tls.allowed-clients"));
    }

    @Test
    void clientCertificatesWithAnAllowlistGetTheFilter() {
        runner.withPropertyValues("server.ssl.client-auth=need", "oj.tls.allowed-clients=edge")
                .run(ctx -> assertThat(ctx).hasSingleBean(PeerCertificateFilter.class));
    }

    @Test
    void noClientCertificatesAndNoAllowlistStartWithoutTheFilter() {
        runner.withPropertyValues("server.ssl.client-auth=none")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(PeerCertificateFilter.class);
                });
    }

    @Test
    void noFilterInReactiveApp() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OjTlsAutoConfiguration.class))
                .withPropertyValues("oj.tls.allowed-clients=edge")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PeerCertificateFilter.class));
    }
}
