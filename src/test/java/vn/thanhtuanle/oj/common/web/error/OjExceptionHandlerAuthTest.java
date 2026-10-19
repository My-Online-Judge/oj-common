package vn.thanhtuanle.oj.common.web.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** An anonymous caller refused by @PreAuthorize gets 401 (the portal refreshes on it); an authenticated one gets 403. */
class OjExceptionHandlerAuthTest {

    private final OjExceptionHandler handler = new OjExceptionHandler();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousGets401() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        var response = handler.handleAccessDeniedException(new AccessDeniedException("Access Denied"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Unauthorized: Full authentication is required to access this resource");
    }

    @Test
    void noAuthenticationGets401() {
        SecurityContextHolder.clearContext();

        var response = handler.handleAccessDeniedException(new AccessDeniedException("Access Denied"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().getStatus()).isEqualTo(401);
    }

    @Test
    void authenticatedGets403() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "alice", "n/a", AuthorityUtils.createAuthorityList("problem:create")));

        var response = handler.handleAccessDeniedException(new AccessDeniedException("Access Denied"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getMessage()).isEqualTo("Access Denied: Access Denied");
    }
}
