package vn.thanhtuanle.oj.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void idComesFromTheUidClaim() {
        var jwt = TestTokens.decoderFor(TestTokens.KEY).decode(TestTokens.valid());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), "alice"));

        assertThat(currentUser.id()).isEqualTo(TestTokens.USER_ID);
    }

    @Test
    void failsLoudlyWhenTheRequestIsNotAuthenticatedByAnAccessToken() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", null));

        assertThatThrownBy(currentUser::id).isInstanceOf(IllegalStateException.class);
    }
}
