package vn.thanhtuanle.oj.common.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import vn.thanhtuanle.oj.common.web.payload.ApiResponse;

import java.io.IOException;

@Slf4j
public class OjAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        log.warn("Unauthenticated request to {}: {}", request.getRequestURI(), authException.getMessage());

        // 401, not 403: the caller has no valid authentication, which a token refresh
        // can fix. 403 is reserved for OjAccessDeniedHandler (authenticated but
        // lacking the required permission) — the portal must not retry those.
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Object> apiResponse = ApiResponse.error(HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized: " + authException.getMessage());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
