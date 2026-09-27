package vn.thanhtuanle.oj.common.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class OjJwtAuthenticationFilterTest {

    private final OjJwtAuthenticationFilter filter =
            new OjJwtAuthenticationFilter(TestTokens.decoderFor(TestTokens.KEY));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("the chain always continues").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/submissions");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void validBearerTokenAuthenticatesWithItsAuthorities() throws Exception {
        Authentication auth = run(bearer(TestTokens.valid()));

        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ADMIN", "problem:create");
    }

    @Test
    void validCookieTokenAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/submissions");
        request.setCookies(new Cookie("deviceTheme", "dark"), new Cookie("accessToken", TestTokens.valid()));

        assertThat(run(request)).isNotNull();
    }

    @Test
    void expiredTokenLeavesTheRequestAnonymous() throws Exception {
        // Beyond the 60s clock skew: an expired token that the 1-day cookie can still carry.
        Instant past = Instant.now().minusSeconds(3600);
        String expired = TestTokens.sign(TestTokens.KEY, TestTokens.validClaims()
                .issueTime(Date.from(past.minusSeconds(900))).expirationTime(Date.from(past)).build());

        assertThat(run(bearer(expired))).isNull();
    }

    @Test
    void tokenSignedByAnotherKeyLeavesTheRequestAnonymous() throws Exception {
        String forged = TestTokens.sign(TestTokens.generate("test-kid"), TestTokens.validClaims().build());

        assertThat(run(bearer(forged))).isNull();
    }

    @Test
    void tokenWithoutUidLeavesTheRequestAnonymous() throws Exception {
        String oldFormat = TestTokens.sign(TestTokens.KEY,
                TestTokens.validClaims().claim(OjJwtDecoders.UID_CLAIM, null).build());

        assertThat(run(bearer(oldFormat))).isNull();
    }

    @Test
    void garbageTokenLeavesTheRequestAnonymous() throws Exception {
        assertThat(run(bearer("not-a-jwt"))).isNull();
    }

    @Test
    void requestWithoutTokenStaysAnonymous() throws Exception {
        assertThat(run(new MockHttpServletRequest("GET", "/api/v1/problems"))).isNull();
    }

    @Test
    void anExistingAuthenticationIsNotReplaced() throws Exception {
        TestingAuthenticationToken existing = new TestingAuthenticationToken("bob", null);
        SecurityContextHolder.getContext().setAuthentication(existing);

        assertThat(run(bearer(TestTokens.valid()))).isSameAs(existing);
    }
}
