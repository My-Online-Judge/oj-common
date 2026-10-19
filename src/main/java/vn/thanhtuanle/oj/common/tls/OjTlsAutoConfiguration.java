package vn.thanhtuanle.oj.common.tls;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Servlet services that set {@code oj.tls.allowed-clients} only answer callers whose certificate CN is listed. A
 * service whose server asks for client certificates ({@code server.ssl.client-auth}: need or want) without that list
 * does not start ({@link AllowedClients}).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(OjTlsProperties.class)
public class OjTlsAutoConfiguration {

    @Bean
    @Conditional(AllowedClients.Enforced.class)
    public PeerCertificateFilter peerCertificateFilter(OjTlsProperties properties,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new PeerCertificateFilter(AllowedClients.required(properties),
                objectMapper.getIfAvailable(ObjectMapper::new));
    }
}
