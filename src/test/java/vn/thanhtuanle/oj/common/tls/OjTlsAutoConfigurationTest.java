package vn.thanhtuanle.oj.common.tls;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import jakarta.servlet.FilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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

    @Test
    void noFilterInReactiveApp() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OjTlsAutoConfiguration.class))
                .withPropertyValues("oj.tls.allowed-clients=edge")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PeerCertificateFilter.class));
    }
}
