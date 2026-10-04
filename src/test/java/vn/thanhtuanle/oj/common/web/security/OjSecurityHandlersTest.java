package vn.thanhtuanle.oj.common.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 401 means "authenticate (or refresh) and retry"; 403 means "authenticated, not allowed" — never retried. */
class OjSecurityHandlersTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void anUnauthenticatedRequestGets401WithTheSameStatusInItsBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OjAuthenticationEntryPoint().commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("Full authentication is required to access this resource"));

        assertThat(response.getStatus()).isEqualTo(401);
        Map<?, ?> body = json.readValue(response.getContentAsString(), Map.class);
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.get("message"))
                .isEqualTo("Unauthorized: Full authentication is required to access this resource");
    }

    @Test
    void aForbiddenRequestGets403WithTheSameStatusInItsBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OjAccessDeniedHandler().handle(new MockHttpServletRequest(), response, new AccessDeniedException("Access Denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        Map<?, ?> body = json.readValue(response.getContentAsString(), Map.class);
        assertThat(body.get("status")).isEqualTo(403);
        assertThat(body.get("message")).isEqualTo("Access Denied: Access Denied");
    }
}
