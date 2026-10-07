package vn.thanhtuanle.oj.common.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.Delay;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.ClientResourcesBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.LettuceClientConfigurationBuilder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisResilienceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisResilienceAutoConfiguration.class));

    @Test
    void aCommandSentWhileDisconnectedIsRejectedAtOnceAndBootsTimeoutsAreKept() {
        runner.run(context -> {
            // What Boot has set before the customizers run: its connect timeout must survive.
            LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
            builder.clientOptions(ClientOptions.builder()
                    .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(250)).build()).build());

            context.getBean(LettuceClientConfigurationBuilderCustomizer.class).customize(builder);

            ClientOptions options = builder.build().getClientOptions().orElseThrow();
            assertThat(options.getDisconnectedBehavior()).isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
            assertThat(options.isAutoReconnect()).isTrue();
            assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofMillis(250));
        });
    }

    @Test
    void reconnectAttemptsAreAtMostOneSecondApart() {
        runner.run(context -> {
            ClientResources.Builder builder = ClientResources.builder();
            context.getBean(ClientResourcesBuilderCustomizer.class).customize(builder);
            ClientResources resources = builder.build();
            try {
                Delay delay = resources.reconnectDelay();
                assertThat(delay.createDelay(1)).isLessThan(Duration.ofSeconds(1));
                assertThat(delay.createDelay(30)).isEqualTo(Duration.ofSeconds(1));
            } finally {
                resources.shutdown();
            }
        });
    }

    @Test
    void lettuceResolvesRedisThroughTheJdkSoTheJvmsDnsCachePolicyApplies() {
        // With netty-resolver-dns on the classpath (the gateway's case) Lettuce would pick Netty's resolver, which
        // keeps an answer for its DNS TTL: Docker's 600 s (final review I1).
        runner.run(context -> {
            ClientResources.Builder builder = ClientResources.builder();
            context.getBean(ClientResourcesBuilderCustomizer.class).customize(builder);
            ClientResources resources = builder.build();
            try {
                assertThat(resources.addressResolverGroup()).isSameAs(DefaultAddressResolverGroup.INSTANCE);
            } finally {
                resources.shutdown();
            }
        });
    }

    @Test
    void withoutLettuceNothingIsConfigured() {
        runner.withClassLoader(new FilteredClassLoader(RedisClient.class)).run(context -> assertThat(context)
                .doesNotHaveBean(LettuceClientConfigurationBuilderCustomizer.class)
                .doesNotHaveBean(ClientResourcesBuilderCustomizer.class));
    }

    @Test
    void isRegisteredAsAnAutoConfiguration() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(RedisResilienceAutoConfiguration.class.getName());
    }
}
