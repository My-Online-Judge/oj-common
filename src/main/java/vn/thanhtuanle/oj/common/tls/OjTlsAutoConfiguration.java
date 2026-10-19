package vn.thanhtuanle.oj.common.tls;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Set;

/** Servlet services that set {@code oj.tls.allowed-clients} only answer callers whose certificate CN is listed. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(OjTlsProperties.class)
public class OjTlsAutoConfiguration {

    @Bean
    @ConditionalOnExpression("!'${oj.tls.allowed-clients:}'.isBlank()")
    public PeerCertificateFilter peerCertificateFilter(OjTlsProperties properties) {
        return new PeerCertificateFilter(Set.copyOf(properties.allowedClients()));
    }
}
