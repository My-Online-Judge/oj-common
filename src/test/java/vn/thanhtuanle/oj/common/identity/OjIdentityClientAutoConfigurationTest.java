package vn.thanhtuanle.oj.common.identity;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import java.util.concurrent.atomic.AtomicInteger;
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
    void negativeTtlIsConfigurable() {
        runner.withPropertyValues("oj.identity.base-url=http://identity:8000", "oj.identity.negative-cache-ttl=5s")
                .run(ctx -> {
                    assertThat(ctx.getBean(OjIdentityClientProperties.class).negativeCacheTtl())
                            .isEqualTo(java.time.Duration.ofSeconds(5));
                    assertThat(ReflectionTestUtils.getField(ctx.getBean(PublicUserClient.class), "negativeTtl"))
                            .isEqualTo(java.time.Duration.ofSeconds(5));
                });
    }

    @Test
    void negativeTtlDefaultsToAMinute() {
        runner.withPropertyValues("oj.identity.base-url=http://identity:8000")
                .run(ctx -> assertThat(ReflectionTestUtils.getField(ctx.getBean(PublicUserClient.class), "negativeTtl"))
                        .isEqualTo(java.time.Duration.ofSeconds(60)));
    }

    @Test
    void usesBootsRestClientBuilderSoCallsAreObserved() {
        AtomicInteger customised = new AtomicInteger();
        runner.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withBean(RestClientCustomizer.class, () -> (Builder b) -> customised.incrementAndGet())
                .withPropertyValues("oj.identity.base-url=http://identity:8000")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PublicUserClient.class);
                    assertThat(customised.get()).isEqualTo(1);
                });
    }

    @Test
    void fallsBackToThePlainBuilderWithoutBoots() {
        runner.withPropertyValues("oj.identity.base-url=http://identity:8000")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(PublicUserClient.class));
    }

    @Test
    void failuresAreCountedOnTheContextsMeterRegistry() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("oj.identity.base-url=http://127.0.0.1:1")
                .run(ctx -> {
                    ctx.getBean(PublicUserClient.class).byIds(java.util.List.of(java.util.UUID.randomUUID()));
                    assertThat(ctx.getBean(MeterRegistry.class)
                            .counter("oj.identity.client.failures", "call", "by_ids").count()).isEqualTo(1.0);
                });
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
