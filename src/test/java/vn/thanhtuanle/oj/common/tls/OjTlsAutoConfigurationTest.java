package vn.thanhtuanle.oj.common.tls;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

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
}
