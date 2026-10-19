package vn.thanhtuanle.oj.common.tls;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;
import java.util.Set;

/** Servlet services that set {@code oj.tls.allowed-clients} only answer callers whose certificate CN is listed. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(OjTlsProperties.class)
public class OjTlsAutoConfiguration {

    @Bean
    @Conditional(AllowedClientsConfigured.class)
    public PeerCertificateFilter peerCertificateFilter(OjTlsProperties properties,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new PeerCertificateFilter(Set.copyOf(properties.allowedClients()),
                objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /** Binds the list, so comma form, YAML lists and {@code [0]=} indexed properties all count. */
    static class AllowedClientsConfigured extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            List<String> clients = Binder.get(context.getEnvironment())
                    .bind("oj.tls.allowed-clients", List.class).orElse(List.of());
            return clients.isEmpty() ? ConditionOutcome.noMatch("oj.tls.allowed-clients is empty")
                    : ConditionOutcome.match("oj.tls.allowed-clients is set");
        }
    }
}
