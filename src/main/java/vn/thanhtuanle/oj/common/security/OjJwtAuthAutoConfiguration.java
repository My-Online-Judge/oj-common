package vn.thanhtuanle.oj.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Wires JWT verification for a servlet service that sets {@code oj.security.jwt.jwks-uri}. The
 * service adds {@link OjJwtAuthenticationFilter} to its own SecurityFilterChain.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.security.oauth2.jwt.NimbusJwtDecoder",
        "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken"})
@ConditionalOnProperty(prefix = "oj.security.jwt", name = "jwks-uri")
@EnableConfigurationProperties(OjJwtProperties.class)
public class OjJwtAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder ojJwtDecoder(OjJwtProperties properties) {
        return OjJwtDecoders.fromJwksUri(properties.jwksUri());
    }

    @Bean
    public OjJwtAuthenticationFilter ojJwtAuthenticationFilter(JwtDecoder decoder) {
        return new OjJwtAuthenticationFilter(decoder);
    }

    /**
     * Spring Boot would otherwise also register the filter bean with the servlet container, where
     * it runs outside Spring Security — which then resets the security context it set.
     */
    @Bean
    public FilterRegistrationBean<OjJwtAuthenticationFilter> ojJwtAuthenticationFilterRegistration(
            OjJwtAuthenticationFilter filter) {
        FilterRegistrationBean<OjJwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public CurrentUser currentUser() {
        return new CurrentUser();
    }
}
