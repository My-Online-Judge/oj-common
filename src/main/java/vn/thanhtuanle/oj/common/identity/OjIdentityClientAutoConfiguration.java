package vn.thanhtuanle.oj.common.identity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/** Wires {@link PublicUserClient} for a service that sets {@code oj.identity.base-url}. */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "oj.identity", name = "base-url")
@EnableConfigurationProperties(OjIdentityClientProperties.class)
public class OjIdentityClientAutoConfiguration {

    static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    @ConditionalOnMissingBean
    public PublicUserClient publicUserClient(OjIdentityClientProperties properties,
                                             ObjectProvider<SslBundles> sslBundles) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);
        if (StringUtils.hasText(properties.sslBundle())) {
            settings = settings.withSslBundle(sslBundles.getObject().getBundle(properties.sslBundle()));
        }
        RestClient rest = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        return new PublicUserClient(rest, Clock.systemUTC(), properties.cacheTtl());
    }
}
