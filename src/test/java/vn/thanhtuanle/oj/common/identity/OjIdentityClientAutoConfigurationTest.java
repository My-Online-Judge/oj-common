package vn.thanhtuanle.oj.common.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OjIdentityClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class, OjIdentityClientAutoConfiguration.class));

    @Test
    void noClientWithoutBaseUrl() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(PublicUserClient.class));
    }

    @Test
    void clientWithBaseUrl() {
        runner.withPropertyValues("oj.identity.base-url=http://identity:8000")
                .run(ctx -> assertThat(ctx).hasSingleBean(PublicUserClient.class));
    }

    @Test
    void sslBundleIsApplied() {
        runner.withPropertyValues(
                        "oj.identity.base-url=https://identity:8000",
                        "oj.identity.ssl-bundle=oj",
                        "spring.ssl.bundle.pem.oj.keystore.certificate=classpath:tls/submission-service.crt",
                        "spring.ssl.bundle.pem.oj.keystore.private-key=classpath:tls/submission-service.key",
                        "spring.ssl.bundle.pem.oj.truststore.certificate=classpath:tls/ca.crt")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(PublicUserClient.class);
                });
    }

    @Test
    void unknownSslBundleFailsStartup() {
        runner.withPropertyValues(
                        "oj.identity.base-url=https://identity:8000",
                        "oj.identity.ssl-bundle=nope")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
