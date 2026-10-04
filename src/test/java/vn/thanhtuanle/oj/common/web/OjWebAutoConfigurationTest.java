package vn.thanhtuanle.oj.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import vn.thanhtuanle.oj.common.web.filter.RequestLoggingFilter;
import vn.thanhtuanle.oj.common.web.security.OjAccessDeniedHandler;
import vn.thanhtuanle.oj.common.web.security.OjAuthenticationEntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class OjWebAutoConfigurationTest {

    private final WebApplicationContextRunner servlet = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OjWebAutoConfiguration.class));

    @Test
    void aServletServiceGetsTheSecurityHandlersAndTheRequestLog() {
        servlet.run(context -> assertThat(context)
                .hasSingleBean(OjAuthenticationEntryPoint.class)
                .hasSingleBean(OjAccessDeniedHandler.class)
                .hasSingleBean(RequestLoggingFilter.class));
    }

    @Test
    void aServiceThatHasItsOwnEntryPointKeepsIt() {
        servlet.withUserConfiguration(OwnEntryPoint.class).run(context -> assertThat(context)
                .hasSingleBean(AuthenticationEntryPoint.class)
                .doesNotHaveBean(OjAuthenticationEntryPoint.class));
    }

    @Test
    void theReactiveGatewayGetsNothing() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OjWebAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(RequestLoggingFilter.class)
                        .doesNotHaveBean(OjAuthenticationEntryPoint.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnEntryPoint {
        @Bean
        AuthenticationEntryPoint ownEntryPoint() {
            return (request, response, e) -> response.sendError(401);
        }
    }
}
