package vn.thanhtuanle.oj.common.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.resource.Delay;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.ClientResourcesBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * How every service's Lettuce client behaves when Redis goes away (sub-project 6). By default Lettuce queues a
 * command sent while it is disconnected and holds it until the command timeout, and waits up to 30 s between
 * reconnect attempts: a stopped Redis cost every request a timeout, and a returned one went unused for up to half a
 * minute. Here a command sent while disconnected fails at once, so the caller's fail-open path runs without waiting,
 * and reconnect attempts are at most a second apart. Boot's own client options (its timeouts) are kept.
 */
@AutoConfiguration
@ConditionalOnClass({RedisClient.class, LettuceClientConfiguration.class})
public class RedisResilienceAutoConfiguration {

    static final Duration MAX_RECONNECT_DELAY = Duration.ofSeconds(1);

    @Bean
    LettuceClientConfigurationBuilderCustomizer ojRejectCommandsWhileDisconnected() {
        return builder -> {
            // Boot has already set its client options (timeouts, socket options): change them, never replace them.
            ClientOptions current = builder.build().getClientOptions().orElseGet(ClientOptions::create);
            builder.clientOptions(current.mutate()
                    .autoReconnect(true)
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .build());
        };
    }

    @Bean
    ClientResourcesBuilderCustomizer ojShortReconnectDelay() {
        return builder -> builder.reconnectDelay(
                Delay.exponential(Duration.ZERO, MAX_RECONNECT_DELAY, 2, TimeUnit.MILLISECONDS));
    }
}
