package vn.thanhtuanle.oj.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import vn.thanhtuanle.oj.common.web.filter.RequestLoggingFilter;
import vn.thanhtuanle.oj.common.web.security.OjAccessDeniedHandler;
import vn.thanhtuanle.oj.common.web.security.OjAuthenticationEntryPoint;

/**
 * Servlet services get the request log, and — when Spring Security is present — the JSON 401/403 handlers their
 * SecurityConfig plugs into exception handling. A service that defines its own handler keeps it. The reactive
 * gateway gets none of this.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OjWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.web.AuthenticationEntryPoint")
    static class SecurityHandlers {

        @Bean
        @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
        public OjAuthenticationEntryPoint ojAuthenticationEntryPoint() {
            return new OjAuthenticationEntryPoint();
        }

        @Bean
        @ConditionalOnMissingBean(AccessDeniedHandler.class)
        public OjAccessDeniedHandler ojAccessDeniedHandler() {
            return new OjAccessDeniedHandler();
        }
    }
}
